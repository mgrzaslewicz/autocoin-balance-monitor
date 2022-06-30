package autocoin.balance.oauth.server

import io.undertow.security.idm.Account
import io.undertow.server.HttpServerExchange
import java.security.Principal

fun CheckTokenDto.toUserAccount() = UserAccount(userName, userAccount.userAccountId, authorities)

fun HttpServerExchange.userAccountId() = this.securityContext.authenticatedAccount.principal.name

data class UserAccount(
    val userName: String,
    val userAccountId: String,
    val authorities: Set<String>
) : Account {
    override fun getRoles() = authorities
    override fun getPrincipal() = Principal { userAccountId }
}

fun HttpServerExchange.isUserInProPlan() = this.securityContext.authenticatedAccount.roles.contains("ROLE_PRO_USER")
