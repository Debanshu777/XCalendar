package com.debanshu.xcalendar.common

import co.touchlab.kermit.Logger

/**
 * Application-wide logger utility using Kermit.
 * 
 * Usage:
 * ```
 * AppLogger.d { "Debug message" }
 * AppLogger.e(throwable) { "Error message" }
 * ```
 */
object AppLogger {
    private val logger = Logger.withTag("XCalendar")
    
    /**
     * Log a verbose message
     */
    fun v(message: () -> String) {
        logger.v { message() }
    }
    
    /**
     * Log a verbose message with throwable
     */
    fun v(throwable: Throwable, message: () -> String) {
        logger.v(throwable) { message() }
    }
    
    /**
     * Log a debug message
     */
    fun d(message: () -> String) {
        logger.d { message() }
    }
    
    /**
     * Log a debug message with throwable
     */
    fun d(throwable: Throwable, message: () -> String) {
        logger.d(throwable) { message() }
    }
    
    /**
     * Log an info message
     */
    fun i(message: () -> String) {
        logger.i { message() }
    }
    
    /**
     * Log an info message with throwable
     */
    fun i(throwable: Throwable, message: () -> String) {
        logger.i(throwable) { message() }
    }
    
    /**
     * Log a warning message
     */
    fun w(message: () -> String) {
        logger.w { message() }
    }
    
    /**
     * Log a warning message with throwable
     */
    fun w(throwable: Throwable, message: () -> String) {
        logger.w(throwable) { message() }
    }
    
    /**
     * Log an error message
     */
    fun e(message: () -> String) {
        logger.e { message() }
    }
    
    /**
     * Log an error message with throwable
     */
    fun e(throwable: Throwable, message: () -> String) {
        logger.e(throwable) { message() }
    }
    
    /**
     * Log an assert message
     */
    fun a(message: () -> String) {
        logger.a { message() }
    }
    
    /**
     * Log an assert message with throwable
     */
    fun a(throwable: Throwable, message: () -> String) {
        logger.a(throwable) { message() }
    }
    
    /**
     * Create a tagged logger for a specific component
     */
    fun withTag(tag: String): Logger = Logger.withTag("XCalendar:$tag")
}

