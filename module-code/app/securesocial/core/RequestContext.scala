package securesocial.core

import play.api.mvc._


/**
 *
 * @author Vladislav Isenbaev (vladislav.isenbaev@odnoklassniki.ru)
 */

trait RequestContext[A] extends Request[A] {
  protected def stackedAction: Option[Result] = None

  def apply: Result = stackedAction.getOrElse(action)

  def action: Result
}