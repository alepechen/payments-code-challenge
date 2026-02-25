package com.checkout.payment.gateway.client;

import com.checkout.payment.gateway.model.BankResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import java.io.IOException;

interface BankClient {

  BankResponse authorize(PostPaymentRequest request) throws IOException, InterruptedException;
}