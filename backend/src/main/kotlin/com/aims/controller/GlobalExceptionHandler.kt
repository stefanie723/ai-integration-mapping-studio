package com.aims.controller

import com.aims.application.dto.ApiResponse
import com.aims.infrastructure.mcp.core.McpException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleBadRequest(ex: IllegalArgumentException): ApiResponse<Nothing> =
        ApiResponse(success = false, message = ex.message)

    @ExceptionHandler(IllegalStateException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleConflict(ex: IllegalStateException): ApiResponse<Nothing> =
        ApiResponse(success = false, message = ex.message)

    @ExceptionHandler(McpException::class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    fun handleMcp(ex: McpException): ApiResponse<Nothing> =
        ApiResponse(success = false, message = ex.message)

    @ExceptionHandler(Exception::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleOther(ex: Exception): ApiResponse<Nothing> =
        ApiResponse(success = false, message = ex.message ?: "Internal error")
}
