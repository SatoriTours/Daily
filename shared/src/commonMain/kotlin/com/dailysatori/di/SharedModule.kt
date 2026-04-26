package com.dailysatori.di

import com.dailysatori.service.import.ImportService
import org.koin.core.module.Module
import org.koin.dsl.module

val sharedModule: Module = module {
    single { ImportService(get(), get()) }
}
