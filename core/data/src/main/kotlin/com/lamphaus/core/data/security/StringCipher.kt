package com.lamphaus.core.data.security

interface StringCipher {
    fun encrypt(value: String): String
    fun decrypt(value: String): String
}

