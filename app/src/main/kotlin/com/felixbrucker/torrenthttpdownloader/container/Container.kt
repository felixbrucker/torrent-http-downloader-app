package com.felixbrucker.torrenthttpdownloader.container



import java.util.concurrent.ConcurrentHashMap

class Container {
    companion object {
        val services = ConcurrentHashMap<String, Any>()
        val serviceBuilders = ConcurrentHashMap<String, ServiceBuilder>()

        fun registerServiceBuilder(builder: ServiceBuilder): Companion {
            serviceBuilders[builder.NAME] = builder

            return this
        }

        fun registerService(name: String, service: Any): Companion {
            services[name] = service

            return this
        }

        @Suppress("UNCHECKED_CAST")
        fun <T>getService(name: String): T {
            val service = services[name]
            if (service != null) {
                return service as T
            }
            val builder = serviceBuilders[name] ?: throw Exception("Service $name not found")
            return services.computeIfAbsent(name) { builder.build() } as T
        }

        @Suppress("UNCHECKED_CAST")
        fun <T>getOptionalService(name: String): T? {
            val service = services[name]
            if (service != null) {
                return service as T
            }
            val builder = serviceBuilders[name] ?: return null
            return services.computeIfAbsent(name) { builder.build() } as T
        }

        fun clear() {
            services.clear()
            serviceBuilders.clear()
        }
    }
}
