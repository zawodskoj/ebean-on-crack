package com.faintstructure.ebeanoncrack.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.metadata.ImmutableKmType
import com.squareup.kotlinpoet.metadata.KotlinPoetMetadataPreview
import kotlinx.metadata.KmClassifier

val ClassName.jvmName: String
    get() = this.packageName.replace('.', '/') + '/' + this.simpleName

fun String.asJvmClassName(): ClassName {
    val split = this.split("/")

    val packageName = split.dropLast(1).joinToString(".")
    val className = split.last().split(".")

    return ClassName(packageName, className)
}

@KotlinPoetMetadataPreview
val ImmutableKmType.classClassifier: KmClassifier.Class?
    get() = this.classifier as? KmClassifier.Class

@KotlinPoetMetadataPreview
val ImmutableKmType.jvmName: String?
    get() = this.classClassifier?.name

@KotlinPoetMetadataPreview
fun ImmutableKmType.asTypeName(): TypeName? = this.classClassifier?.let {
    val className = it.name.asJvmClassName()

    if (this.arguments.isNotEmpty()) {
        val mapped = this.arguments.map { it.type?.asTypeName() }
        if (mapped.any { it == null })
            return null

        @Suppress("UNCHECKED_CAST")
        className.parameterizedBy(mapped as List<TypeName>)
    } else {
        className
    }
}