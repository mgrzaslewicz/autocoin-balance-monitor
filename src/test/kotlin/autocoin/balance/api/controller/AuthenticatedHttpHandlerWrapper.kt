package autocoin.balance.api.controller

import autocoin.balance.api.HttpHandlerWrapper
import autocoin.balance.oauth.server.UserAccount
import io.undertow.security.api.AuthenticationMechanism
import io.undertow.security.api.AuthenticationMode
import io.undertow.security.api.SecurityContext
import io.undertow.security.handlers.AuthenticationCallHandler
import io.undertow.security.handlers.AuthenticationConstraintHandler
import io.undertow.security.handlers.AuthenticationMechanismsHandler
import io.undertow.security.handlers.SecurityInitialHandler
import io.undertow.security.idm.Account
import io.undertow.security.idm.Credential
import io.undertow.security.idm.IdentityManager
import io.undertow.server.HttpHandler
import io.undertow.server.HttpServerExchange
import io.undertow.util.StatusCodes
import java.util.*

class AuthenticatedHttpHandlerWrapper : HttpHandlerWrapper {
    private val userAccount = UserAccount(
        userName = "some-user@non-existing-domain",
        userAccountId = UUID.randomUUID().toString(),
        authorities = emptySet(),
    )
    val userAccountId = userAccount.userAccountId

    override fun wrap(next: HttpHandler): HttpHandler {
        return SecurityInitialHandler(
            AuthenticationMode.PRO_ACTIVE,
            object : IdentityManager {
                override fun verify(account: Account) = account
                override fun verify(id: String?, credential: Credential?) = null
                override fun verify(credential: Credential?) = null
            },
            AuthenticationMechanismsHandler(
                AuthenticationConstraintHandler(AuthenticationCallHandler(next)),
                listOf(object : AuthenticationMechanism {
                    override fun authenticate(exchange: HttpServerExchange?, securityContext: SecurityContext?): AuthenticationMechanism.AuthenticationMechanismOutcome {
                        securityContext?.authenticationComplete(userAccount, "test authentication", false)
                        return AuthenticationMechanism.AuthenticationMechanismOutcome.AUTHENTICATED
                    }

                    override fun sendChallenge(exchange: HttpServerExchange?, securityContext: SecurityContext?): AuthenticationMechanism.ChallengeResult {
                        return AuthenticationMechanism.ChallengeResult(true, StatusCodes.UNAUTHORIZED)
                    }
                })
            )
        )
    }
}
