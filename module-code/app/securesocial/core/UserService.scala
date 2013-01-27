/**
 * Copyright 2012 Jorge Aliss (jaliss at gmail dot com) - twitter: @jaliss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package securesocial.core

import play.api.{Logger, Plugin, Application}
import providers.{UsernamePasswordProvider, Token}
import play.api.libs.concurrent.Akka
import akka.actor.Cancellable
import scala.concurrent.duration._
import play.api.libs.concurrent.Execution.Implicits._

/**
 * A trait that provides the means to find and save users
 * for the SecureSocial module.
 *
 * @see DefaultUserService
 */
trait UserService {

  /**
   * Finds a SocialUser that maches the specified id
   *
   * @param id the user id
   * @return an optional user
   */
  def find(id: UserId):Option[Identity]

  /**
   * Finds a Social user by email and provider id.
   *
   * Note: If you do not plan to use the UsernamePassword provider just provide en empty
   * implementation.
   *
   * @param email - the user email
   * @param providerId - the provider id
   * @return
   */
  def findByEmailAndProvider(email: String, providerId: String):Option[Identity]

  /**
   * Saves the user.  This method gets called when a user logs in.
   * This is your chance to save the user information in your backing store.
   * @param user
   */
  def save(user: Identity)

  /**
   * Saves a token.  This is needed for users that
   * are creating an account in the system instead of using one in a 3rd party system.
   *
   * Note: If you do not plan to use the UsernamePassword provider just provide en empty
   * implementation
   *
   * @param token The token to save
   * @return A string with a uuid that will be embedded in the welcome email.
   */
  def save(token: Token)


  /**
   * Finds a token
   *
   * Note: If you do not plan to use the UsernamePassword provider just provide en empty
   * implementation
   *
   * @param token the token id
   * @return
   */
  def findToken(token: String): Option[Token]

  /**
   * Deletes a token
   *
   * Note: If you do not plan to use the UsernamePassword provider just provide en empty
   * implementation
   *
   * @param uuid the token id
   */
  def deleteToken(uuid: String)

  /**
   * Deletes all expired tokens
   *
   * Note: If you do not plan to use the UsernamePassword provider just provide en empty
   * implementation
   *
   */
  def deleteExpiredTokens()
}

/**
 * Base class for the classes that implement UserService.  Since this is a plugin it gets loaded
 * at application start time.  Only one plugin of this type must be specified in the play.plugins file.
 *
 * @param application
 */
abstract class UserServicePlugin(application: Application) extends Plugin with UserService {
  val DefaultInterval = 5
  val DeleteIntervalKey = "securesocial.userpass.tokenDeleteInterval"

  var cancellable: Option[Cancellable] = None

  override def onStop() {
    cancellable.map( _.cancel() )
  }

  /**
   * Registers this object so SecureSocial can invoke it.
   */
  override def onStart() {
    import play.api.Play.current
    val i = application.configuration.getInt(DeleteIntervalKey).getOrElse(DefaultInterval)

    cancellable = if ( UsernamePasswordProvider.enableTokenJob ) {
      Some(
        Akka.system.scheduler.schedule(0 seconds, i minutes) {
          if ( Logger.isDebugEnabled ) {
            Logger.debug("[securesocial] calling deleteExpiredTokens()")
          }
          deleteExpiredTokens()
        }
      )
    } else None

    UserService.setService(this)
    Logger.info("[securesocial] loaded user service: %s".format(this.getClass))
  }
}

/**
 * The UserService singleton
 */
object UserService {
  var delegate: Option[UserService] = None

  def setService(service: UserService) {
    delegate = Some(service)
  }

  def find(id: UserId):Option[Identity] = {
    delegate.map( _.find(id) ).getOrElse {
      notInitialized()
      None
    }
  }

  def findByEmailAndProvider(email: String, providerId: String):Option[Identity] = {
    delegate.map( _.findByEmailAndProvider(email, providerId) ).getOrElse {
      notInitialized()
      None
    }
  }

  def save(user: Identity) {
    delegate.map( _.save(user) ).getOrElse {
      notInitialized()
    }
  }

  def save(token: Token) {
    delegate.map( _.save(token) ).getOrElse {
      notInitialized()
    }
  }

  def findToken(token: String): Option[Token] =  {
    delegate.map( _.findToken(token)).getOrElse {
      notInitialized()
      None
    }
  }

  def deleteToken(token: String) {
    delegate.map( _.deleteToken(token)).getOrElse {
      notInitialized()
    }
  }


  private def notInitialized() {
    Logger.error("[securesocial] UserService was not initialized. Make sure a UserService plugin is specified in your play.plugins file")
    throw new RuntimeException("UserService not initialized")
  }
}
