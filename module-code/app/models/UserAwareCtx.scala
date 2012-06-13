package models

import securesocial.core.SocialUser

/**
 * Author: Vladislav Isenbaev (isenbaev@gmail.com)
 */

case class UserAwareCtx(maybeUser: Option[SocialUser])
