package com.wallettransfer.externaltransfers.event;

import java.util.UUID;

public record ExternalTransferRequestedEvent(
        UUID transferId, String providerRequestReference, String beneficiaryToken, String amount, String currency) {}
