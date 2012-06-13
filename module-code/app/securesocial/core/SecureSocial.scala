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

import play.api.mvc._
import securesocial.controllers.routes
import play.api.i18n.Messages
import play.api.Logger
import play.api.libs.json.Json
import models._

/**
 * Provides the actions that can be used to protect controllers and retrieve the current user
 * if available.
 *
 * object MyController extends SecureSocial {
 *    def protectedAction = SecuredAction() { implicit request =>
 *      Ok("Hello %s".format(request.user.displayName))
 *    }
 */
trait SecureSocial extends Controller {

  def removeCredentials(session: Session) = session - SecureSocial.UserKey - SecureSocial.ProviderKey

  /**
   * A Forbidden response for API clients
   * @param request
   * @tparam A
   * @return
   */
  private def apiClientForbidden[A](implicit request: Request[A]): Result = {
    Forbidden(Json.toJson(Map("error" -> "Credentials required"))).withSession {
      removeCredentials(session)
    }.as(JSON)
  }

  /**
   * Return the logout call.
   * @return the logout call
   */
  protected def logoutCall = {
    routes.LoginPage.logout()
  }

  /**
   * Return the login call.
   * @return the login call
   */
  protected def loginCall = {
    routes.LoginPage.login()
  }

  /**
   * A secured action.  If there is no user in the session the request is redirected
   * to the login page
   */
  trait SecuredRequestContext[A] extends RequestContext[A] with UserAwareRequestContext[A] {
    def apiClient = false
    
    implicit lazy val securedCtx = new SecuredCtx(userAwareCtx.maybeUser.get)

    override protected def stackedAction = super.stackedAction.orElse{
      userAwareCtx.maybeUser match {
        case Some(_) => None
        case None => Some{
          if (apiClient) {
            apiClientForbidden(this)
          } else {
            if (Logger.isDebugEnabled) {
              Logger.debug("Anonymous user trying to access : '%s'".format(this.uri))
            }
            Redirect(loginCall).flashing(SecureSocial.ErrorKey -> Messages("securesocial.loginRequired")).withSession(
              removeCredentials(session) + (SecureSocial.OriginalUrlKey -> this.uri)
            )
          }
        }
      }
    }
  }

  trait UserAwareRequestContext[A] extends RequestContext[A] {
    implicit lazy val userAwareCtx = new UserAwareCtx(SecureSocial.userFromSession(this).flatMap(UserService.find))
  }
}

object SecureSocial {
  val UserKey = "securesocial.user"
  val ProviderKey = "securesocial.provider"
  val OriginalUrlKey = "securesocial.originalUrl"
  val ErrorKey = "securesocial.error"

  /**
   * Build a UserId object from the session data
   *
   * @param request
   * @tparam A
   * @return
   */
  def userFromSession[A](implicit request: Request[A]):Option[UserId] = {
    for (
      userId <- request.session.get(SecureSocial.UserKey);
      providerId <- request.session.get(SecureSocial.ProviderKey)
    ) yield {
      UserId(userId, providerId)
    }
  }

  /**
   * Get the current logged in user.  This method can be used from public actions that need to
   * access the current user if there's any
   *
   * @param request
   * @tparam A
   * @return
   */
  def currentUser[A](implicit request: Request[A]):Option[SocialUser] = {
    for (
      userId <- userFromSession ;
      user <- UserService.find(userId)
    ) yield {
      fillServiceInfo(user)
    }
  }

  def fillServiceInfo(user: SocialUser): SocialUser = {
    if ( user.authMethod == AuthenticationMethod.OAuth1 ) {
      // if the user is using OAuth1 make sure we're also returning
      // the right service info
      ProviderRegistry.get(user.id.providerId).map { p =>
        val si = p.asInstanceOf[OAuth1Provider].serviceInfo
        val oauthInfo = user.oAuth1Info.get.copy(serviceInfo = si)
        user.copy(oAuth1Info = Some(oauthInfo))
      }.get
    } else {
      user
    }
  }
}
