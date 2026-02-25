package com.checkout.payment.gateway.repository;

import com.checkout.payment.gateway.model.PostPaymentResponse;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentsRepository {

  private final ConcurrentHashMap<UUID, PostPaymentResponse> payments = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, PostPaymentResponse> idempotencyMap = new ConcurrentHashMap<>();

  public Optional<PostPaymentResponse> get(UUID id) {
    return Optional.ofNullable(payments.get(id));
  }

  public Optional<PostPaymentResponse> getByIdempotencyKey(String key) {
    return Optional.ofNullable(idempotencyMap.get(key));
  }

  public PostPaymentResponse store(PostPaymentResponse payment, String idempotencyKey) {
    if (payment.getId() == null) {
      payment.setId(UUID.randomUUID());
    }
    PostPaymentResponse canonical =
        idempotencyKey != null ? idempotencyMap.putIfAbsent(idempotencyKey, payment) : null;
    PostPaymentResponse toStore = canonical == null ? payment : canonical;
    payments.putIfAbsent(toStore.getId(), toStore);
    return toStore;
  }

}
