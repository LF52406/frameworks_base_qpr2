/*
 * Copyright (C) 2024 The LibreMobileOS Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.systemui.statusbar.pipeline.ims.data.repository

import android.telephony.SubscriptionManager
import android.util.Log
import android.telephony.ims.ImsException
import android.telephony.ims.ImsManager
import android.telephony.ims.ImsMmTelManager
import android.telephony.ims.ImsReasonInfo
import android.telephony.ims.ImsRegistrationAttributes
import android.telephony.ims.ImsStateCallback
import android.telephony.ims.RegistrationManager.RegistrationCallback
import android.telephony.ims.feature.MmTelFeature
import android.telephony.ims.stub.ImsRegistrationImplBase.REGISTRATION_TECH_NONE
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.statusbar.pipeline.ims.data.model.ImsStateModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

interface ImsRepository {
    val subId: Int
    val imsState: StateFlow<ImsStateModel>
}

@OptIn(ExperimentalCoroutinesApi::class)
class ImsRepositoryImpl(
    override val subId: Int,
    imsManager: ImsManager,
    subscriptionManager: SubscriptionManager,
    bgDispatcher: CoroutineDispatcher,
    scope: CoroutineScope,
) : ImsRepository {

    private val imsCallback: StateFlow<ImsCallbackState> = run {
        val initial = ImsCallbackState()
        val imsMmTelManager = imsManager.getImsMmTelManager(subId)
        callbackFlow {
            val registrationCallback = object : RegistrationCallback() {
                override fun onRegistered(attributes: ImsRegistrationAttributes) {
                    Log.i(TAG, "IMS registered subId=$" + "{subId} tech=$" + "{attributes.registrationTechnology}")
                    trySend(CallbackEvent.OnImsRegistrationChanged(true, attributes))
                }

                override fun onUnregistered(info: ImsReasonInfo) {
                    Log.i(TAG, "IMS unregistered subId=$" + "{subId} reason=$" + "{info.code}")
                    trySend(CallbackEvent.OnImsRegistrationChanged(false, null))
                }
            }
            val capabilityCallback = object : ImsMmTelManager.CapabilityCallback() {
                override fun onCapabilitiesStatusChanged(
                    capabilities: MmTelFeature.MmTelCapabilities
                ) {
                    Log.i(
                        TAG,
                        "IMS capabilities subId=$" + "{subId} voice=$" + "{capabilities.isCapable(MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE)}"
                    )
                    trySend(CallbackEvent.OnImsCapabilitiesStatusChanged(capabilities))
                }
            }
            val stateCallback = object : ImsStateCallback() {
                override fun onAvailable() {
                    try {
                        imsMmTelManager.registerImsRegistrationCallback(
                            bgDispatcher.asExecutor(), registrationCallback
                        )
                        imsMmTelManager.registerMmTelCapabilityCallback(
                            bgDispatcher.asExecutor(), capabilityCallback
                        )
                    } catch (e: Exception) {
                        // Without closing the flow a failed registration leaves the icon
                        // permanently stuck on its initial false state.
                        Log.e(TAG, "Failed to attach MMTEL callbacks subId=$" + "{subId}", e)
                        close(e)
                    }
                }

                override fun onUnavailable(reason: Int) {
                    Log.i(TAG, "IMS unavailable subId=$" + "{subId} reason=$" + "{reason}")
                    // Drop stale LTE voice state while telephony disconnects.
                    trySend(CallbackEvent.OnImsUnavailable)
                    runCatching {
                        imsMmTelManager.unregisterImsRegistrationCallback(registrationCallback)
                    }
                    runCatching {
                        imsMmTelManager.unregisterMmTelCapabilityCallback(capabilityCallback)
                    }
                }

                override fun onError() {
                    // Android deregisters ImsStateCallback after a telephony crash.
                    // Recreate the complete subscription instead of waiting forever.
                    Log.w(TAG, "IMS state callback lost subId=$" + "{subId}; restarting")
                    close(ImsCallbackRestartException(subId))
                }
            }
            imsMmTelManager.registerImsStateCallback(
                bgDispatcher.asExecutor(), stateCallback
            )
            awaitClose {
                runCatching { imsMmTelManager.unregisterImsStateCallback(stateCallback) }
                runCatching {
                    imsMmTelManager.unregisterImsRegistrationCallback(registrationCallback)
                }
                runCatching {
                    imsMmTelManager.unregisterMmTelCapabilityCallback(capabilityCallback)
                }
            }
        }
            // Keep scan inside retry so a re-registration cannot reuse stale IMS state.
            .scan(initial) { state, event -> state.applyEvent(event) }
            .retryWhen { cause, _ ->
                val recoverable = cause is ImsCallbackRestartException ||
                    (cause is ImsException &&
                        cause.code == ImsException.CODE_ERROR_SERVICE_UNAVAILABLE)
                if (recoverable) {
                    Log.w(TAG, "Retrying IMS callbacks subId=$" + "{subId}", cause)
                    delay(1000)
                }
                recoverable
            }
            .catch { cause ->
                Log.e(TAG, "IMS callback stream failed subId=$" + "{subId}", cause)
                emit(initial)
            }
            .stateIn(scope = scope, started = SharingStarted.WhileSubscribed(), initial)
    }

    override val imsState: StateFlow<ImsStateModel> =
        imsCallback
            .map { callbackState ->
                val registrationChanged = callbackState.onImsRegistrationChanged
                val capabilitiesChanged = callbackState.onImsCapabilitiesStatusChanged
                val registered = registrationChanged?.registered ?: false
                val capabilities = capabilitiesChanged?.capabilities
                val registrationTech = registrationChanged?.attributes?.registrationTechnology
                    ?: REGISTRATION_TECH_NONE
                ImsStateModel(
                    subId = subId,
                    slotIndex = SubscriptionManager.getSlotIndex(subId),
                    activeSubCount = subscriptionManager.activeSubscriptionInfoCount,
                    registered = registered,
                    capabilities = capabilities,
                    registrationTech = registrationTech
                )
            }
            .catch { emit(ImsStateModel()) /* on exception, just return default value */ }
            .stateIn(scope, SharingStarted.WhileSubscribed(), ImsStateModel())

    private companion object {
        const val TAG = "SystemUI.ImsRepository"
    }

    class Factory
    @Inject
    constructor(
        private val imsManager: ImsManager,
        private val subscriptionManager: SubscriptionManager,
        @Background private val bgDispatcher: CoroutineDispatcher,
        @Application private val scope: CoroutineScope,
    ) {
        fun build(subId: Int): ImsRepository {
            return ImsRepositoryImpl(
                subId = subId,
                imsManager = imsManager,
                subscriptionManager = subscriptionManager,
                bgDispatcher = bgDispatcher,
                scope = scope,
            )
        }
    }
}

private class ImsCallbackRestartException(subId: Int) :
    RuntimeException("IMS callback needs re-registration for subId=$" + "{subId}")

sealed interface CallbackEvent {
    /** Clears registration and capabilities when the MMTEL service becomes unavailable. */
    data object OnImsUnavailable : CallbackEvent

    data class OnImsRegistrationChanged(
        val registered: Boolean,
        val attributes: ImsRegistrationAttributes?
    ) : CallbackEvent

    data class OnImsCapabilitiesStatusChanged(
        val capabilities: MmTelFeature.MmTelCapabilities
    ) : CallbackEvent
}

data class ImsCallbackState(
    val onImsRegistrationChanged: CallbackEvent.OnImsRegistrationChanged? = null,
    val onImsCapabilitiesStatusChanged: CallbackEvent.OnImsCapabilitiesStatusChanged? = null
) {
    fun applyEvent(event: CallbackEvent): ImsCallbackState {
        return when (event) {
            CallbackEvent.OnImsUnavailable -> ImsCallbackState()
            is CallbackEvent.OnImsRegistrationChanged -> copy(onImsRegistrationChanged = event)
            is CallbackEvent.OnImsCapabilitiesStatusChanged -> copy(onImsCapabilitiesStatusChanged = event)
        }
    }
}
