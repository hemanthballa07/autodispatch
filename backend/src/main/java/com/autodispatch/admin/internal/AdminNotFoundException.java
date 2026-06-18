package com.autodispatch.admin.internal;

class AdminNotFoundException extends RuntimeException {

    AdminNotFoundException(String message) {
        super(message);
    }
}
