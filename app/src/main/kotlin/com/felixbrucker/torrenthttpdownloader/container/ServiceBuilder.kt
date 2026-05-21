package com.felixbrucker.torrenthttpdownloader.container

interface ServiceBuilder {
    val NAME: String
    fun build(): Any
}