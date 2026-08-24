package com.leo.erp.auth.service;

import com.leo.erp.auth.api.AccountQuery;
import com.leo.erp.auth.api.AuthenticationAccountQuery;
import com.leo.erp.auth.domain.enums.UserStatus;
import com.leo.erp.auth.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class AccountQueryService implements AccountQuery, AuthenticationAccountQuery {

    private final UserAccountRepository userAccountRepository;

    public AccountQueryService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    public Optional<AccountSnapshot> findById(Long userId) {
        return userAccountRepository.findByIdAndDeletedFlagFalse(userId)
                .map(account -> new AccountSnapshot(
                        account.getId(),
                        account.getLoginName(),
                        account.getUserName(),
                        account.getLastLoginDate()
                ));
    }

    @Override
    public Optional<AuthenticatedAccountSnapshot> findActiveById(Long userId) {
        return userAccountRepository.findByIdAndDeletedFlagFalse(userId)
                .filter(account -> account.getStatus() == UserStatus.NORMAL)
                .map(account -> new AuthenticatedAccountSnapshot(
                        account.getId(),
                        account.getLoginName(),
                        normalizeCredentialVersion(account.getCredentialVersion())
                ));
    }

    private long normalizeCredentialVersion(Long credentialVersion) {
        return credentialVersion == null ? 0L : credentialVersion;
    }
}
