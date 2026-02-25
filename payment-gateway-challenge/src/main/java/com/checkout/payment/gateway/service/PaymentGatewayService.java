package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.client.HttpBankClient;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.model.BankResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PaymentGatewayService {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayService.class);

  private final PaymentsRepository paymentsRepository;
  private final HttpBankClient bankClient;
  public PaymentGatewayService(PaymentsRepository paymentsRepository,HttpBankClient bankClient) {
    this.paymentsRepository = paymentsRepository;
    this.bankClient =bankClient;
  }

  public PostPaymentResponse getPaymentById(UUID id) {
    LOG.debug("Requesting access to to payment with ID {}", id);
    return paymentsRepository.get(id).orElseThrow(() -> new EventProcessingException("Invalid ID"));
  }

  public PostPaymentResponse processPayment(String idempotencyKey, PostPaymentRequest request) {
    if (idempotencyKey != null) {
      Optional<PostPaymentResponse> existing = paymentsRepository.getByIdempotencyKey(idempotencyKey);
      if (existing.isPresent()) return existing.get();
    }

    BankResponse bankResponse = bankClient.authorize(request);
    PostPaymentResponse response = PostPaymentResponse.from(request,bankResponse);
    LOG.debug("Saving payment with ID {}", response.getId());
    return paymentsRepository.store(response, idempotencyKey);
  }
}

