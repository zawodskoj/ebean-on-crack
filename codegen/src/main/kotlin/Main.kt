package com.faintstructure.ebeanoncrack.codegen

import com.faintstructure.ebeanoncrack.EntityOnCrack
import com.faintstructure.ebeanoncrack.types.LKnownOwner
import com.faintstructure.ebeanoncrack.types.LProperty
import com.faintstructure.ebeanoncrack.types.LSimpleListProperty
import com.faintstructure.ebeanoncrack.types.LSimpleProperty
import com.google.auto.service.AutoService
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.metadata.*
import kotlinx.metadata.KmClassifier
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement
import java.io.File
import java.sql.Types

@AutoService(Processor::class) // For registering the service
@SupportedSourceVersion(SourceVersion.RELEASE_8) // to support Java 8
@SupportedOptions(FileGenerator.KAPT_KOTLIN_GENERATED_OPTION_NAME)
@KotlinPoetMetadataPreview
class FileGenerator : AbstractProcessor(){

    override fun getSupportedAnnotationTypes(): MutableSet<String> {
        return mutableSetOf(EntityOnCrack::class.java.name)
    }

    override fun getSupportedSourceVersion(): SourceVersion {
        return SourceVersion.latest()
    }

    data class Property(val owner: ImmutableKmClass, val property: ImmutableKmProperty)
    data class Arrow(val from: ImmutableKmClass, val to: ImmutableKmType, val name: String)

    fun writeFileSpec(spec: FileSpec) {
        val kaptKotlinGeneratedDir = processingEnv.options[KAPT_KOTLIN_GENERATED_OPTION_NAME]
        val file = File(kaptKotlinGeneratedDir, spec.name)
        file.writeText(spec.toString())
    }

    fun classNameToQualifiedName(className: ClassName): String {
        return className.packageName.replace('.', '/') + '/' + className.simpleName
    }

    fun qualifiedNameToClassName(qualifiedName: String): ClassName {
        val split = qualifiedName.split("/")

        val packageName = split.dropLast(1).joinToString(".")
        val className = split.last().split(".")

        return ClassName(packageName, className)
    }

    fun typeToQualifiedName(type: ImmutableKmType): String {
        return when (val classifier = type.classifier) {
            is KmClassifier.Class -> classifier.name
            else -> throw null!! // TODO: message of unsupported classifier
        }
    }

    fun typeToClassName(type: ImmutableKmType): TypeName {
        return when (val classifier = type.classifier) {
            is KmClassifier.Class -> {
                val className = qualifiedNameToClassName(classifier.name)
                if (type.arguments.isNotEmpty()) {
                    className.parameterizedBy(type.arguments.map { typeToClassName(it.type ?: throw null!! /* TODO message*/) })
                } else {
                    className
                }
            }
            else -> throw null!! // TODO: message of unsupported classifier
        }
    }

    fun createEntityTypes(packageName: String, entityClass: ImmutableKmClass, allEntityClasses: List<ImmutableKmClass>) {
        val entityClassName = qualifiedNameToClassName(entityClass.name)

        val interfaceName = "LI${entityClassName.simpleName}"
        val className = "LC${entityClassName.simpleName}"
        val unboxedArrowName = "LUA${entityClassName.simpleName}"
        val listArrowName = "LLA${entityClassName.simpleName}"
        val objectName = "L${entityClassName.simpleName}"

        val fileSpec = FileSpec.builder(packageName, "L${entityClassName.simpleName}.kt")
            .addImport("com.faintstructure.ebeanoncrack.types", "appendPathComponent")
            .addType(TypeSpec.interfaceBuilder(interfaceName)
                .let {
                    val rootTypeVar = TypeVariableName("Root")
                    val knownOwnerType = LKnownOwner::class.asClassName().parameterizedBy(rootTypeVar, entityClassName)
                    var base = it.addTypeVariable(rootTypeVar)
                        .addSuperinterface(knownOwnerType)

                    for (property in entityClass.properties) {
                        val propertyOriginalType = typeToClassName(property.returnType)

                        if (allEntityClasses.any { it.name == typeToQualifiedName(property.returnType) }) {
                            propertyOriginalType as ClassName
                            val arrowTypeName = ClassName(packageName, "LUA${propertyOriginalType.simpleName}").parameterizedBy(rootTypeVar)
                            base = base.addProperty(PropertySpec.builder(property.name, arrowTypeName).build())
                        } else {
                            val propertyType = when {
                                propertyOriginalType is ParameterizedTypeName &&
                                        propertyOriginalType.rawType.reflectionName() == List::class.asClassName().reflectionName() -> {

                                    val typeArgClassName = propertyOriginalType.typeArguments[0] as ClassName
                                    if (allEntityClasses.any { it.name == classNameToQualifiedName(typeArgClassName) }) {
                                        ClassName(packageName, "LLA${typeArgClassName.simpleName}").parameterizedBy(rootTypeVar)
                                    } else {
                                        LSimpleListProperty::class.asClassName().parameterizedBy(rootTypeVar,
                                            propertyOriginalType.typeArguments[0])
                                    }
                                }
                                else ->
                                    LSimpleProperty::class.asClassName().parameterizedBy(rootTypeVar, propertyOriginalType)
                            }
                            base = base.addProperty(PropertySpec.builder(property.name, propertyType).build())
                        }
                    }

                    base
                }
                .build())
            .addType(TypeSpec.classBuilder(className)
                .let {
                    val rootTypeVar = TypeVariableName("Root")
                    var base = it.addModifiers(KModifier.SEALED)
                        .addTypeVariable(rootTypeVar)
                        .addSuperinterface(ClassName(packageName, interfaceName).parameterizedBy(rootTypeVar))
                        .primaryConstructor(FunSpec.constructorBuilder()
                            .addParameter("_propPath", String::class.asClassName().copy(nullable = true))
                            .build())
                        .addProperty(PropertySpec.builder("_propPath", String::class.asClassName().copy(nullable = true))
                            .initializer("_propPath")
                            .addModifiers(KModifier.PRIVATE)
                            .build())

                    for (property in entityClass.properties) {
                        val propertyOriginalType = typeToClassName(property.returnType)

                        if (allEntityClasses.any { it.name == typeToQualifiedName(property.returnType) }) {
                            propertyOriginalType as ClassName
                            val arrowTypeName = ClassName(packageName, "LUA${propertyOriginalType.simpleName}").parameterizedBy(rootTypeVar)
                            base = base.addProperty(PropertySpec.builder(property.name, arrowTypeName)
                                .addModifiers(KModifier.OVERRIDE)
                                .getter(FunSpec.getterBuilder()
                                    .addCode("return %T(_propPath.appendPathComponent(%S), %S)", arrowTypeName, property.name, property.name)
                                    .build())
                                .build())
                        } else {
                            val propertyType = when {
                                propertyOriginalType is ParameterizedTypeName &&
                                        propertyOriginalType.rawType.reflectionName() == List::class.asClassName().reflectionName() -> {

                                    val typeArgClassName = propertyOriginalType.typeArguments[0] as ClassName
                                    if (allEntityClasses.any { it.name == classNameToQualifiedName(typeArgClassName) }) {
                                        ClassName(packageName, "LLA${typeArgClassName.simpleName}").parameterizedBy(rootTypeVar)
                                    } else {
                                        LSimpleListProperty::class.asClassName().parameterizedBy(rootTypeVar,
                                            propertyOriginalType.typeArguments[0])
                                    }
                                }
                                else ->
                                    LSimpleProperty::class.asClassName().parameterizedBy(rootTypeVar, propertyOriginalType)
                            }
                            base = base.addProperty(PropertySpec.builder(property.name, propertyType)
                                .addModifiers(KModifier.OVERRIDE)
                                .getter(FunSpec.getterBuilder()
                                    .addCode("return %T(_propPath.appendPathComponent(%S), %S)", propertyType, property.name, property.name)
                                    .build())
                                .build())
                        }
                    }

                    base
                }
                .build())
            .addType(TypeSpec.classBuilder(unboxedArrowName)
                .let {
                    val rootTypeVar = TypeVariableName("Root")
                    it.addTypeVariable(rootTypeVar)
                        .superclass(ClassName(packageName, className).parameterizedBy(rootTypeVar))
                        .addSuperclassConstructorParameter(CodeBlock.of("_propPath"))
                        .addSuperinterface(LProperty::class.asClassName().parameterizedBy(rootTypeVar, entityClassName, entityClassName))
                        .primaryConstructor(FunSpec.constructorBuilder()
                            .addParameter("_propPath", String::class.asClassName())
                            .addParameter("_propName", String::class.asClassName())
                            .build())
                        .addProperty(PropertySpec.builder("_propPath", String::class.asClassName())
                            .initializer("_propPath")
                            .addModifiers(KModifier.PRIVATE)
                            .build())
                        .addProperty(PropertySpec.builder("_propName", String::class.asClassName())
                            .initializer("_propName")
                            .addModifiers(KModifier.PRIVATE)
                            .build())
                        .addFunction(FunSpec.builder("getPropertyPath")
                            .returns(String::class)
                            .addModifiers(KModifier.OVERRIDE)
                            .addCode("return _propPath")
                            .build())
                        .addFunction(FunSpec.builder("getPropertyName")
                            .returns(String::class)
                            .addModifiers(KModifier.OVERRIDE)
                            .addCode("return _propName")
                            .build())
                }
                .build())
            .addType(TypeSpec.classBuilder(listArrowName)
                .let {
                    val rootTypeVar = TypeVariableName("Root")
                    it.addTypeVariable(rootTypeVar)
                        .superclass(ClassName(packageName, className).parameterizedBy(rootTypeVar))
                        .addSuperclassConstructorParameter(CodeBlock.of("_propPath"))
                        .addSuperinterface(LProperty::class.asClassName().parameterizedBy(rootTypeVar, List::class.asClassName().parameterizedBy(entityClassName), entityClassName))
                        .primaryConstructor(FunSpec.constructorBuilder()
                            .addParameter("_propPath", String::class.asClassName())
                            .addParameter("_propName", String::class.asClassName())
                            .build())
                        .addProperty(PropertySpec.builder("_propPath", String::class.asClassName())
                            .initializer("_propPath")
                            .addModifiers(KModifier.PRIVATE)
                            .build())
                        .addProperty(PropertySpec.builder("_propName", String::class.asClassName())
                            .initializer("_propName")
                            .addModifiers(KModifier.PRIVATE)
                            .build())
                        .addFunction(FunSpec.builder("getPropertyPath")
                            .returns(String::class)
                            .addModifiers(KModifier.OVERRIDE)
                            .addCode("return _propPath")
                            .build())
                        .addFunction(FunSpec.builder("getPropertyName")
                            .returns(String::class)
                            .addModifiers(KModifier.OVERRIDE)
                            .addCode("return _propName")
                            .build())
                }
                .build())
            .addType(TypeSpec.objectBuilder(objectName)
                .superclass(ClassName(packageName, className).parameterizedBy(entityClassName))
                .addSuperclassConstructorParameter("null")
                .build())

        writeFileSpec(fileSpec.build())
    }

    override fun process(set: MutableSet<out TypeElement>, roundEnvironment: RoundEnvironment): Boolean {
        val entityElements = roundEnvironment.getElementsAnnotatedWith(EntityOnCrack::class.java)
        val entityClasses = entityElements.map { it.getAnnotation(Metadata::class.java).toImmutableKmClass() }

        println("Entity classes:")
        val arrowPackage = "realarrows"

        for (entityClass in entityClasses) {
            println("\t" + entityClass.name)
            createEntityTypes(arrowPackage, entityClass, entityClasses)
        }

        return true
    }

    companion object {
        const val KAPT_KOTLIN_GENERATED_OPTION_NAME = "kapt.kotlin.generated"
    }

}