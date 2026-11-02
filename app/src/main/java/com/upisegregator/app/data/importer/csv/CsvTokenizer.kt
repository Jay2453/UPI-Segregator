package com.upisegregator.app.data.importer.csv

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Robust CSV tokenizer that adheres to RFC 4180 rules.
 *
 * Supports:
 * - Comma delimiters.
 * - Quoted fields containing commas, line-breaks, or spaces.
 * - Escaped double quotes ("").
 * - Varied line endings (\r\n, \n, \r).
 * - Empty fields.
 */
object CsvTokenizer {

    /**
     * Parses an input stream into rows of string columns.
     */
    fun parse(inputStream: InputStream, charsetName: String = "UTF-8"): List<List<String>> {
        val reader = BufferedReader(InputStreamReader(inputStream, charsetName))
        val rows = mutableListOf<List<String>>()

        var currentChar: Int
        var inQuotes = false
        val currentField = StringBuilder()
        val currentRow = mutableListOf<String>()

        while (reader.read().also { currentChar = it } != -1) {
            val c = currentChar.toChar()

            when {
                c == '"' -> {
                    if (inQuotes) {
                        // Lookahead for escaped quote ("")
                        reader.mark(1)
                        val next = reader.read()
                        if (next != -1 && next.toChar() == '"') {
                            currentField.append('"')
                        } else {
                            reader.reset()
                            inQuotes = false
                        }
                    } else {
                        inQuotes = true
                    }
                }

                c == ',' && !inQuotes -> {
                    currentRow.add(currentField.toString().trim())
                    currentField.setLength(0)
                }

                (c == '\n' || c == '\r') && !inQuotes -> {
                    if (c == '\r') {
                        reader.mark(1)
                        val next = reader.read()
                        if (next != -1 && next.toChar() != '\n') {
                            reader.reset()
                        }
                    }
                    currentRow.add(currentField.toString().trim())
                    currentField.setLength(0)

                    // Ignore completely blank lines
                    if (currentRow.any { it.isNotBlank() }) {
                        rows.add(currentRow.toList())
                    }
                    currentRow.clear()
                }

                else -> {
                    currentField.append(c)
                }
            }
        }

        // Add any remaining field/row at EOF
        if (currentField.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(currentField.toString().trim())
            if (currentRow.any { it.isNotBlank() }) {
                rows.add(currentRow.toList())
            }
        }

        return rows
    }

    /**
     * Parses a raw CSV string into rows of string columns.
     */
    fun parseString(csvContent: String): List<List<String>> {
        return parse(csvContent.byteInputStream(Charsets.UTF_8))
    }
}
