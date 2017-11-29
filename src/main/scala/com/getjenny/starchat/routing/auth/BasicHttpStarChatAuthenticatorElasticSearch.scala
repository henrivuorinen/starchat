package com.getjenny.starchat.routing.auth

import akka.http.scaladsl.server.directives.Credentials
import com.roundeights.hasher.Implicits._
import com.typesafe.config.{Config, ConfigFactory}
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}
import com.getjenny.starchat.entities.{User, Permissions}
import akka.event.{Logging, LoggingAdapter}
import com.getjenny.starchat.SCActorSystem
import scala.concurrent.ExecutionContext.Implicits.global

class BasicHttpStarChatAuthenticatorElasticSearch extends StarChatAuthenticator {
  val config: Config = ConfigFactory.load()
  val log: LoggingAdapter = Logging(SCActorSystem.system, this.getClass.getCanonicalName)
  val admin: String = config.getString("starchat.basic_http_es.admin")
  val password: String = config.getString("starchat.basic_http_es.password")
  val salt: String = config.getString("starchat.basic_http_es.salt")

  val userService: UserService = UserFactory.apply(SupportedAuthImpl.basic_http_es)

  def secret(password: String, salt: String): String = {
    password + "#" + salt
  }

  def hashed_secret(password: String, salt: String): String = {
    secret(password, salt).sha512
  }

  class Hasher(salt: String) {
    def hasher(password: String): String = {
      secret(password, salt).sha512
    }
  }

  def fetchUser(id: String): Future[User] = {
    userService.read(id)
  }

  def authenticator(credentials: Credentials): Future[Option[User]] = {
    credentials match {
      case p@Credentials.Provided(id) =>
        val user_request = Await.ready(fetchUser(id), 5.seconds).value.get
        user_request match {
          case Success(user) =>
            val hasher = new Hasher(user.salt)
            if (p.verify(secret = user.password, hasher = hasher.hasher)) {
              Future {
                Some(user)
              }
            } else {
              val message = "Authentication failed for the user: " + "user.id with password(" + user.password + ")"
              log.error(message)
              Future.successful(None)
            }
          case Failure(e) =>
            val message: String = "unable to match credentials"
            log.error(message)
            Future.successful(None)
        }
      case _ => Future.successful(None)
    }
  }

  def hasPermissions(user: User, index: String, permission: Permissions.Value): Future[Boolean] = {
    user.id match {
      case `admin` => //admin can do everything
        val user_permissions = user.permissions.getOrElse("admin", Set.empty[Permissions.Value])
        Future.successful(user_permissions.contains(Permissions.admin))
      case _ =>
        val user_permissions = user.permissions.getOrElse(index, Set.empty[Permissions.Value])
        Future.successful(user_permissions.contains(permission))
    }
  }
}