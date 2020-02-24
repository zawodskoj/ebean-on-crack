package com.faintstructure.ebeanoncrack

import com.faintstructure.ebeanoncrack.types.*

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class EntityOnCrack

interface Query<T, LT>

fun <T> Query<T, *>.select(vararg props: LProperty<T, *, *>) {

}

fun <T, LT> Query<T, LT>.where(whereExpr: WhereExpression) {

}

fun <T> Query<T, *>.orderBy(vararg props: LProperty<T, *, *>) {

}

data class UpdateBlk<T>(val pairs: List<Pair<LProperty<T, *, *>, *>>) {
    fun <X> set(pair: Pair<LProperty<T, X, *>, X>) =
        UpdateBlk(pairs + pair)
}

fun <T> Query<T, *>.update(updateBlk: (u: UpdateBlk<T>) -> UpdateBlk<T>) {

}

sealed class WhereExpression
data class EqExpression(val path: String, val value: Any) : WhereExpression()
data class AndExpression(val first: WhereExpression, val second: WhereExpression) : WhereExpression()

infix fun <T, B : Any, U> LProperty<T, B, U>.eq(v: B): WhereExpression = EqExpression(this.getPropertyPath(), v)
infix fun WhereExpression.and(other: WhereExpression): WhereExpression =
    AndExpression(this, other)

fun <T, B : Any, U> LProperty<T, B, U>.asc(): LProperty<T, B, U> = this
fun <T, B : Any, U> LProperty<T, B, U>.desc(): LProperty<T, B, U> = this
