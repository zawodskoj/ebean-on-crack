package com.faintstructure.ebeanoncrack.codegen

import com.faintstructure.ebeanoncrack.EntityOnCrack
import com.faintstructure.ebeanoncrack.types.LKnownOwner
import com.faintstructure.ebeanoncrack.types.LProperty
import com.faintstructure.ebeanoncrack.types.LSimpleListProperty
import com.faintstructure.ebeanoncrack.types.LSimpleProperty
import com.google.auto.service.AutoService
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.metadata.*
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement
import java.io.File

@AutoService(Processor::class) // For registering the service
@SupportedSourceVersion(SourceVersion.RELEASE_8) // to support Java 8
@SupportedOptions(FileGenerator.KAPT_KOTLIN_GENERATED_OPTION_NAME)
@KotlinPoetMetadataPreview
class FileGenerator : AbstractProcessor() {

    override fun getSupportedAnnotationTypes(): MutableSet<String> {
        return mutableSetOf(EntityOnCrack::class.java.name)
    }

    override fun getSupportedSourceVersion(): SourceVersion {
        return SourceVersion.latest()
    }

    private fun writeFileSpec(spec: FileSpec) {
        val kaptKotlinGeneratedDir = processingEnv.options[KAPT_KOTLIN_GENERATED_OPTION_NAME]
        val file = File(kaptKotlinGeneratedDir, spec.name)
        file.writeText(spec.toString())
    }

    private fun lObjectTypeName(entityClassName: ClassName) = "L${entityClassName.simpleName}"
    private fun lClassTypeName(entityClassName: ClassName) = "LC${entityClassName.simpleName}"
    private fun lInterfaceName(entityClassName: ClassName) = "LI${entityClassName.simpleName}"
    private fun lUnboxedArrowName(entityClassName: ClassName) = "LUA${entityClassName.simpleName}"
    private fun lListArrowName(entityClassName: ClassName) = "LLA${entityClassName.simpleName}"

    private fun buildEntityObjectType(packageName: String, entityClassName: ClassName): TypeSpec {
        return TypeSpec.objectBuilder(lObjectTypeName(entityClassName))
            .superclass(ClassName(packageName, lClassTypeName(entityClassName)).parameterizedBy(entityClassName))
            .addSuperclassConstructorParameter("null")
            .build()
    }

    private enum class ArrowType { UNBOXED, LIST }

    private fun buildArrowClassType(packageName: String, entityClassName: ClassName, arrowType: ArrowType): TypeSpec {
        val rootTypeVar = TypeVariableName("Root")
        val name = when (arrowType) {
            ArrowType.LIST -> lListArrowName(entityClassName)
            ArrowType.UNBOXED -> lUnboxedArrowName(entityClassName)
        }
        val boxedType = when (arrowType) {
            ArrowType.LIST -> List::class.asClassName().parameterizedBy(entityClassName)
            ArrowType.UNBOXED -> entityClassName
        }

        return TypeSpec.classBuilder(name)
            .addTypeVariable(rootTypeVar)
            .superclass(ClassName(packageName, lClassTypeName(entityClassName)).parameterizedBy(rootTypeVar))
            .addSuperclassConstructorParameter(CodeBlock.of("_propPath"))
            .addSuperinterface(LProperty::class.asClassName().parameterizedBy(rootTypeVar, boxedType, entityClassName))
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
            .build()
    }

    private fun getArrowTypeName(packageName: String,
                         rootTypeVar: TypeName,
                         property: ImmutableKmProperty,
                         allEntityClasses: List<ImmutableKmClass>): TypeName? {
        val propertyType = property.returnType.asTypeName() ?: return null

        val isList = propertyType is ParameterizedTypeName &&
                propertyType.rawType.reflectionName() == List::class.asClassName().reflectionName()
        val unboxedType = if (isList) {
            (propertyType as ParameterizedTypeName).typeArguments[0] as? ClassName ?: return null
        } else {
            propertyType as? ClassName ?: return null
        }

        return if (allEntityClasses.any { it.name == unboxedType.jvmName }) {
            ClassName(packageName, if (isList) lListArrowName(unboxedType) else lUnboxedArrowName(unboxedType))
                .parameterizedBy(rootTypeVar)
        } else {
            if (isList) {
                LSimpleListProperty::class.asClassName().parameterizedBy(rootTypeVar, unboxedType)
            } else {
                LSimpleProperty::class.asClassName().parameterizedBy(rootTypeVar, unboxedType)
            }
        }
    }

    private fun buildEntityClassType(packageName: String, entityClassName: ClassName, properties: List<ImmutableKmProperty>, allEntityClasses: List<ImmutableKmClass>): TypeSpec {
        val rootTypeVar = TypeVariableName("Root")
        val propPathType = String::class.asClassName().copy(nullable = true)

        var builder = TypeSpec.classBuilder(lClassTypeName(entityClassName))
            .addModifiers(KModifier.SEALED)
            .addTypeVariable(rootTypeVar)
            .addSuperinterface(ClassName(packageName, lInterfaceName(entityClassName)).parameterizedBy(rootTypeVar))
            .primaryConstructor(FunSpec.constructorBuilder()
                .addParameter("_propPath", propPathType)
                .build())
            .addProperty(PropertySpec.builder("_propPath", propPathType)
                .initializer("_propPath")
                .addModifiers(KModifier.PRIVATE)
                .build())

        for (property in properties) {
            val arrowTypeName = getArrowTypeName(packageName, rootTypeVar, property, allEntityClasses)
                ?: continue

            builder = builder.addProperty(PropertySpec.builder(property.name, arrowTypeName)
                .addModifiers(KModifier.OVERRIDE)
                .getter(FunSpec.getterBuilder()
                    .addCode("return %T(_propPath.appendPathComponent(%S), %S)", arrowTypeName, property.name, property.name)
                    .build())
                .build())
        }

        return builder.build()
    }

    private fun buildInterfaceType(packageName: String, entityClassName: ClassName, properties: List<ImmutableKmProperty>, allEntityClasses: List<ImmutableKmClass>): TypeSpec {
        val rootTypeVar = TypeVariableName("Root")
        val knownOwnerType = LKnownOwner::class.asClassName().parameterizedBy(rootTypeVar, entityClassName)

        var builder = TypeSpec.interfaceBuilder(lInterfaceName(entityClassName))
            .addTypeVariable(rootTypeVar)
            .addSuperinterface(knownOwnerType)

        for (property in properties) {
            val arrowTypeName = getArrowTypeName(packageName, rootTypeVar, property, allEntityClasses)
                ?: continue

            builder = builder.addProperty(PropertySpec.builder(property.name, arrowTypeName).build())
        }

        return builder.build()
    }

    private fun createEntityTypes(packageName: String, entityClass: ImmutableKmClass, allEntityClasses: List<ImmutableKmClass>) {
        val entityClassName = entityClass.name.asJvmClassName()

        val fileSpec = FileSpec.builder(packageName, "L${entityClassName.simpleName}.kt")
            .addImport("com.faintstructure.ebeanoncrack.types", "appendPathComponent")
            .addType(buildInterfaceType(packageName, entityClassName, entityClass.properties, allEntityClasses))
            .addType(buildEntityClassType(packageName, entityClassName, entityClass.properties, allEntityClasses))
            .addType(buildArrowClassType(packageName, entityClassName, ArrowType.UNBOXED))
            .addType(buildArrowClassType(packageName, entityClassName, ArrowType.LIST))
            .addType(buildEntityObjectType(packageName, entityClassName))

        writeFileSpec(fileSpec.build())
    }

    override fun process(set: MutableSet<out TypeElement>, roundEnvironment: RoundEnvironment): Boolean {
        val entityElements = roundEnvironment.getElementsAnnotatedWith(EntityOnCrack::class.java)
        val entityClasses = entityElements.map { it.getAnnotation(Metadata::class.java).toImmutableKmClass() }

        val arrowPackage = "realarrows"

        for (entityClass in entityClasses) {
            createEntityTypes(arrowPackage, entityClass, entityClasses)
        }

        return true
    }

    companion object {
        const val KAPT_KOTLIN_GENERATED_OPTION_NAME = "kapt.kotlin.generated"
    }

}