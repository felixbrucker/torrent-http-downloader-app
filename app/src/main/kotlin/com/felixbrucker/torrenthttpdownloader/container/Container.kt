package com.felixbrucker.torrenthttpdownloader.container



class Container {
    companion object {
        val services = mutableMapOf<String, Any>()
        val serviceBuilders = mutableMapOf<String, ServiceBuilder>()

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
            var service = services[name]
            if (service != null) {
                return service as T
            }
            val builder = serviceBuilders[name] ?: throw Exception("Service $name not found")
            service = builder.build()
            registerService(name, service)

            return service as T
        }

        @Suppress("UNCHECKED_CAST")
        fun <T>getOptionalService(name: String): T? {
            var service = services[name]
            if (service != null) {
                return service as T
            }
            val builder = serviceBuilders[name] ?: return null
            service = builder.build()
            registerService(name, service)

            return service as T
        }

        fun clear() {
            services.clear()
            serviceBuilders.clear()
        }
    }
}