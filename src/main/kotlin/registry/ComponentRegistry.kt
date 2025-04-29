package registry

import javax.swing.JComponent

class ComponentRegistry {

    private val components = mutableMapOf<ComponentId, JComponent>()

    fun register(id: ComponentId, component: JComponent) {
        if (components.containsKey(id)) {
            throw IllegalStateException("Component with id $id already registered")
        }
        components[id] = component
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : JComponent> get(id: ComponentId): T {
        return components[id] as? T
            ?: throw IllegalArgumentException("Component for id $id not found or wrong type")
    }
}
