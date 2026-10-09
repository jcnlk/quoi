package quoi.api.events.core

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Holds every [Subscription] registered for a single [Event] type.
 * All elements are ordered descending by [Subscription.priority]
 */
class SubscriptionRegistry<T : Event> {
    val subscriptions = CopyOnWriteArrayList<Subscription<T>>()
    @Volatile
    var dispatchSnapshot: Array<Subscription<T>> = emptyArray()
        private set

    @Synchronized
    fun add(subscription: Subscription<T>) {
        var index = subscriptions.size
        for (i in subscriptions.indices) {
            if (subscription.priority > subscriptions[i].priority) {
                index = i
                break
            }
        }
        subscriptions.add(index, subscription)
        dispatchSnapshot = subscriptions.toTypedArray()
    }

    @Synchronized
    fun remove(subscription: Subscription<T>) {
        if (subscriptions.remove(subscription)) {
            dispatchSnapshot = subscriptions.toTypedArray()
        }
    }
}