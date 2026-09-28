package com.tienda.pedidos.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

public interface EmailService {
    void enviar(String destinatario, String asunto, String cuerpo);
}

@Service
class EmailServiceImpl implements EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

    @Override
    public void enviar(String destinatario, String asunto, String cuerpo) {
        log.info("Simulando envío de correo a [{}]:\nAsunto: {}\nCuerpo:\n{}", destinatario, asunto, cuerpo);
    }
}