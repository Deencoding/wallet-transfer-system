package com.wallettransfer.providers.client;

import com.wallettransfer.providers.dto.ProviderTransferResult;
import java.math.BigDecimal;

public interface BankTransferProvider {
    ProviderTransferResult create(String requestReference, String beneficiaryToken, BigDecimal amount, String currency);

    ProviderTransferResult queryByRequestReference(String requestReference);
}
