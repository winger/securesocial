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
package securesocial.core.providers.utils

import play.api.mvc.Call
import play.api.Play
import play.api.Logger
import play.api.Play.current

/**
 *
 */
object RoutesHelper {
  // ProviderController
  val pc = Play.application.classloader.loadClass("securesocial.controllers.ReverseProviderController")
  val providerControllerMethods = pc.newInstance().asInstanceOf[{
    def authenticateByPost(p: String): Call
    def authenticate(p: String): Call
    def notAuthorized: Call
  }]

  def authenticateByPost(provider:String): Call = providerControllerMethods.authenticateByPost(provider)
  def authenticate(provider:String): Call = providerControllerMethods.authenticate(provider)
  def notAuthorized: Call = providerControllerMethods.notAuthorized

  // LoginPage
  val lp = Play.application.classloader.loadClass("securesocial.controllers.ReverseLoginPage")
  val loginPageMethods = lp.newInstance().asInstanceOf[{
    def logout(): Call
    def login(): Call
  }]

  def login() = loginPageMethods.login()
  def logout() = loginPageMethods.logout()


  ///
  val rr = Play.application.classloader.loadClass("securesocial.controllers.ReverseRegistration")
  val registrationMethods = rr.newInstance().asInstanceOf[{
    def handleStartResetPassword(): Call
    def handleStartSignUp(): Call
    def handleSignUp(token:String): Call
    def startSignUp(): Call
    def resetPassword(token:String): Call
    def startResetPassword(): Call
    def signUp(token:String): Call
    def handleResetPassword(token:String): Call
  }]

  def handleStartResetPassword() = registrationMethods.handleStartResetPassword()
  def handleStartSignUp() = registrationMethods.handleStartSignUp()
  def handleSignUp(token:String) = registrationMethods.handleSignUp(token)
  def startSignUp() = registrationMethods.startSignUp()
  def resetPassword(token:String) = registrationMethods.resetPassword(token)
  def startResetPassword() = registrationMethods.startResetPassword()
  def signUp(token:String) = registrationMethods.signUp(token)
  def handleResetPassword(token:String) = registrationMethods.handleResetPassword(token)

  ////
  var passChange = Play.application.classloader.loadClass("securesocial.controllers.ReversePasswordChange")
  val passwordChangeMethods = passChange.newInstance().asInstanceOf[{
    def page(): Call
    def handlePasswordChange(): Call
  }]

  def changePasswordPage() = passwordChangeMethods.page()
  def handlePasswordChange() = passwordChangeMethods.handlePasswordChange()

  val assets = {
    val conf = Play.current.configuration
    val clazz = conf.getString("securesocial.assetsController").getOrElse("controllers.ReverseAssets")
    if ( Logger.isDebugEnabled ) {
      Logger.debug("[securesocial] assets controller = %s".format(clazz))
    }
    Play.application.classloader.loadClass(clazz)
  }

  val assetsControllerMethods = assets.newInstance().asInstanceOf[{
    def at(file: String): Call
  }]

  def at(file: String) = assetsControllerMethods.at(file)
}
