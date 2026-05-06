package com.thecircle.contracts.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ContractCreateRequest(String itemId,String ownerId,String receiverId,ContractType type,BigDecimal guaranteeAmount,String conditions,LocalDateTime returnDate



){}
