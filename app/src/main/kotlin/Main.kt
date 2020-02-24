package wtfs

import realarrows.*
import com.faintstructure.ebeanoncrack.*

@EntityOnCrack
class Post(val content: String, val user: User)

@EntityOnCrack
class User(val name: String, val roles: List<Role>)

@EntityOnCrack
class Role(val name: String)

// TODO codegen
class QPost : Query<Post, LPost>

fun main() {
    println(LPost.user.roles.name.getPropertyPath())

    val q = QPost()
    q.select(LPost.content, LPost.user.name)
    q.where(
        (LPost.content eq "123") and (LPost.user.name eq "456")
    )
    q.orderBy(LPost.content.asc(), LPost.user.name.desc())
    q.update { it
        .set(LPost.content to "wtf")
        .set(LPost.user to User("wtf", listOf()))
    }
}