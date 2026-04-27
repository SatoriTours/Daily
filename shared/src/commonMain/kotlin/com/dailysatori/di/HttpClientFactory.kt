package com.dailysatori.di

import io.ktor.client.HttpClient

expect fun createHttpClient(): HttpClient
