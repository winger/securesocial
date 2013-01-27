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
package securesocial.controllers

import play.api.mvc.{Result, Action, Controller}
import play.api.data._
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.Play
import securesocial.core.providers.UsernamePasswordProvider
import securesocial.core.{AuthenticationMethod, UserService}
import com.typesafe.plugin._
import Play.current
import securesocial.core.providers.utils._
import org.joda.time.DateTime
import java.util.UUID
import play.api.i18n.Messages
import securesocial.core.SocialUser
import securesocial.core.providers.Token
import scala.Some
import securesocial.core.UserId
import play.api.Logger


/**
 * A controller to handle user registration.
 *
 */
object Registration extends Controller {

  val providerId = UsernamePasswordProvider.UsernamePassword
  val UserNameAlreadyTaken = "securesocial.signup.userNameAlreadyTaken"
  val PasswordsDoNotMatch = "securesocial.signup.passwordsDoNotMatch"
  val ThankYouCheckEmail = "securesocial.signup.thankYouCheckEmail"
  val InvalidLink = "securesocial.signup.invalidLink"
  val SignUpDone = "securesocial.signup.signUpDone"
  val PasswordUpdated = "securesocial.password.passwordUpdated"
  val ErrorUpdatingPassword = "securesocial.password.error"

  val UserName = "userName"
  val FirstName = "firstName"
  val LastName = "lastName"
  val Password = "password"
  val Password1 = "password1"
  val Password2 = "password2"
  val Email = "email"
  val Success = "success"
  val Error = "error"

  val TokenDurationKey = "securesocial.userpass.tokenDuration"
  val DefaultDuration = 60
  val TokenDuration = Play.current.configuration.getInt(TokenDurationKey).getOrElse(DefaultDuration)

  case class RegistrationInfo(userName: Option[String], firstName: String, lastName: String, password: String)

  val formWithUsername = Form[RegistrationInfo](
    mapping(
      UserName -> nonEmptyText.verifying( Messages(UserNameAlreadyTaken), userName => {
          UserService.find(UserId(userName,providerId)).isEmpty
      }),
      FirstName -> nonEmptyText,
      LastName -> nonEmptyText,
      (Password ->
        tuple(
          Password1 -> nonEmptyText.verifying( use[PasswordValidator].errorMessage,
                                               p => use[PasswordValidator].isValid(p)
                                             ),
          Password2 -> nonEmptyText
        ).verifying(Messages(PasswordsDoNotMatch), passwords => passwords._1 == passwords._2)
      )
    )
    // binding
    ((userName, firstName, lastName, password) => RegistrationInfo(Some(userName), firstName, lastName, password._1))
    // unbinding
    (info => Some(info.userName.getOrElse(""), info.firstName, info.lastName, ("", "")))
  )

  val formWithoutUsername = Form[RegistrationInfo](
    mapping(
      FirstName -> nonEmptyText,
      LastName -> nonEmptyText,
      (Password ->
        tuple(
          Password1 -> nonEmptyText.verifying( use[PasswordValidator].errorMessage,
                                               p => use[PasswordValidator].isValid(p)
                                             ),
          Password2 -> nonEmptyText
        ).verifying(Messages(PasswordsDoNotMatch), passwords => passwords._1 == passwords._2)
      )
    )
      // binding
      ((firstName, lastName, password) => RegistrationInfo(None, firstName, lastName, password._1))
      // unbinding
      (info => Some(info.firstName, info.lastName, ("", "")))
  )

  val form = if ( UsernamePasswordProvider.withUserNameSupport ) formWithUsername else formWithoutUsername

  val startForm = Form (
    Email -> email.verifying( nonEmpty )
  )

  val changePasswordForm = Form (
    Password ->
      tuple(
        Password1 -> nonEmptyText,
        Password2 -> nonEmptyText
      ).verifying(Messages(PasswordsDoNotMatch), passwords => passwords._1 == passwords._2)
  )

  /**
   * Starts the sign up process
   */
  def startSignUp = Action { implicit request =>
    Ok(use[TemplatesPlugin].getStartSignUpPage(request, startForm))
  }

  private def createToken(email: String, isSignUp: Boolean): (String, Token) = {
    val uuid = UUID.randomUUID().toString
    val now = DateTime.now

    val token = Token(
      uuid, email,
      now,
      now.plusMinutes(TokenDuration),
      isSignUp = isSignUp
    )
    UserService.save(token)
    (uuid, token)
  }

  def handleStartSignUp = Action { implicit request =>
    startForm.bindFromRequest.fold (
      errors => {
        BadRequest(use[TemplatesPlugin].getStartSignUpPage(request , errors))
      },
      email => {
        // check if there is already an account for this email address
        UserService.findByEmailAndProvider(email, UsernamePasswordProvider.UsernamePassword) match {
          case Some(user) => {
            // user signed up already, send an email offering to login/recover password
            Mailer.sendAlreadyRegisteredEmail(user)
          }
          case None => {
            val token = createToken(email, isSignUp = true)
            Mailer.sendSignUpEmail(email, token._1)
          }
        }
        Redirect(RoutesHelper.login()).flashing(Success -> Messages(ThankYouCheckEmail))
      }
    )
  }

  /**
   * Renders the sign up page
   * @return
   */
  def signUp(token: String) = Action { implicit request =>
    if ( Logger.isDebugEnabled ) {
      Logger.debug("trying sign up with token %s".format(token))
    }
    executeForToken(token, true, { _ =>
      Ok(use[TemplatesPlugin].getSignUpPage(request, form, token))
    })
  }

  private def executeForToken(token: String, isSignUp: Boolean, f: Token => Result): Result = {
    UserService.findToken(token) match {
      case Some(t) if !t.isExpired && t.isSignUp == isSignUp => {
        f(t)
      }
      case _ =>
        Redirect(RoutesHelper.startSignUp()).flashing(Error -> InvalidLink)
    }
  }

  /**
   * Handles posts from the sign up page
   */
  def handleSignUp(token: String) = Action { implicit request =>
    executeForToken(token, true, { t =>
      form.bindFromRequest.fold (
        errors => {
          if ( Logger.isDebugEnabled ) {
            Logger.debug("errors " + errors)
          }
          BadRequest(use[TemplatesPlugin].getSignUpPage(request, errors, t.uuid))
        },
        info => {
          val id = if ( UsernamePasswordProvider.withUserNameSupport ) info.userName.get else t.email
          val userId = UserId(id, providerId)
          val user = SocialUser(
            userId,
            info.firstName,
            info.lastName,
            "%s %s".format(info.firstName, info.lastName),
            Some(t.email),
            if ( UsernamePasswordProvider.enableGravatar ) GravatarHelper.avatarFor(t.email) else None,
            AuthenticationMethod.UserPassword,
            passwordInfo = Some(use[PasswordHasher].hash(info.password))
          )
          UserService.save(user)
          UserService.deleteToken(t.uuid)
          if ( UsernamePasswordProvider.sendWelcomeEmail ) {
            Mailer.sendWelcomeEmail(user)
          }
          Redirect(RoutesHelper.login()).flashing(Success -> Messages(SignUpDone))
        }
      )
    })
  }

  def startResetPassword = Action { implicit request =>
    Ok(use[TemplatesPlugin].getStartResetPasswordPage(request, startForm ))
  }

  def handleStartResetPassword = Action { implicit request =>
    startForm.bindFromRequest.fold (
      errors => {
        BadRequest(use[TemplatesPlugin].getStartResetPasswordPage(request , errors))
      },
      email => {
        UserService.findByEmailAndProvider(email, UsernamePasswordProvider.UsernamePassword) match {
          case Some(user) => {
            val token = createToken(email, isSignUp = false)
            Mailer.sendPasswordResetEmail(user, token._1)
          }
          case None => {
            Mailer.sendUnkownEmailNotice(email)
          }
        }
        Redirect(RoutesHelper.login()).flashing(Success -> Messages(ThankYouCheckEmail))
      }
    )
  }

  def resetPassword(token: String) = Action { implicit request =>
    executeForToken(token, false, { t =>
      Ok(use[TemplatesPlugin].getResetPasswordPage(request, changePasswordForm, token))
    })
  }

  def handleResetPassword(token: String) = Action { implicit request =>
    executeForToken(token, false, { t=>
      changePasswordForm.bindFromRequest.fold( errors => {
        BadRequest(use[TemplatesPlugin].getResetPasswordPage(request, errors, token))
      },
      p => {
        val toFlash = UserService.findByEmailAndProvider(t.email, UsernamePasswordProvider.UsernamePassword) match {
          case Some(user) => {
            val hashed = use[PasswordHasher].hash(p._1)
            val updated = user.copy( passwordInfo = Some(hashed) )
            UserService.save(updated)
            UserService.deleteToken(token)
            Mailer.sendPasswordChangedNotice(updated)
            (Success -> Messages(PasswordUpdated))
          }
          case _ => {
            Logger.error("Count not find user with email %s during password reset".format(t.email))
            (Error -> Messages(ErrorUpdatingPassword))
          }
        }
        Redirect(RoutesHelper.login()).flashing(toFlash)
      })
    })
  }
}
