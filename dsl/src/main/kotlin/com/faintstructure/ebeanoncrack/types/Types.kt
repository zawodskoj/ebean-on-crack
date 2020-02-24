package com.faintstructure.ebeanoncrack.types

interface LKnownOwner<Root, T>
interface LProperty<Root, Boxed, Unboxed> {
    fun getPropertyPath(): String
    fun getPropertyName(): String
}

typealias LUnboxedProperty<Root, Unboxed> = LProperty<Root, Unboxed, Unboxed>
typealias LListProperty<Root, Unboxed> = LProperty<Root, List<Unboxed>, Unboxed>

class LSimpleProperty<Root, Unboxed>(private val path: String, private val name: String) : LUnboxedProperty<Root, Unboxed> {
    override fun getPropertyPath(): String = path
    override fun getPropertyName(): String = name
}

class LSimpleListProperty<Root, Unboxed>(private val path: String, private val name: String) : LListProperty<Root, Unboxed> {
    override fun getPropertyPath(): String = path
    override fun getPropertyName(): String = name
}

fun String?.appendPathComponent(component: String) = if (this == null) component else "${this}.$component"
