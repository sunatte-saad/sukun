package app.sukun.helper

import android.util.Log
import androidx.annotation.MainThread
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import java.util.concurrent.atomic.AtomicBoolean

class SingleLiveEvent<T> : MutableLiveData<T>() {
    private val pending = AtomicBoolean(false)
    private var eventObserver: Observer<in T>? = null
    private val internalObserver = Observer<T> { value ->
        if (pending.compareAndSet(true, false)) {
            eventObserver?.onChanged(value)
        }
    }

    @MainThread
    override fun observe(owner: LifecycleOwner, observer: Observer<in T>) {
        if (hasObservers()) {
            Log.w("SingleLiveEvent", "Ignoring extra observer because SingleLiveEvent supports only one observer")
            return
        }
        eventObserver = observer
        super.observe(owner, internalObserver)
    }

    @MainThread
    override fun setValue(t: T?) {
        pending.set(true)
        super.setValue(t)
    }

    @MainThread
    fun call() {
        value = null
    }
}