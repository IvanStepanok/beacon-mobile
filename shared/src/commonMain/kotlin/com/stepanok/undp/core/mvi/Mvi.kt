package com.stepanok.undp.core.mvi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Marker interfaces for the MVI contract of a feature. */
interface UiState
interface UiIntent
interface UiEffect

/**
 * MVI base over Voyager's [StateScreenModel]: typed [state] (inherited), an [onIntent] entry point,
 * a reducer-style [setState], and a [Channel]-backed one-shot [effects] stream (navigation / toasts
 * that must not replay on recomposition).
 */
abstract class MviScreenModel<S : UiState, I : UiIntent, E : UiEffect>(
    initialState: S,
) : StateScreenModel<S>(initialState) {

    private val _effects = Channel<E>(Channel.BUFFERED)
    val effects: Flow<E> = _effects.receiveAsFlow()

    protected fun setState(reducer: S.() -> S) {
        mutableState.value = mutableState.value.reducer()
    }

    protected fun postEffect(effect: E) {
        screenModelScope.launch { _effects.send(effect) }
    }

    abstract fun onIntent(intent: I)
}

/** Collects one-shot effects exactly once per composition. */
@Composable
fun <E> Flow<E>.collectAsEffect(onEffect: (E) -> Unit) {
    LaunchedEffect(this) { collect { onEffect(it) } }
}
