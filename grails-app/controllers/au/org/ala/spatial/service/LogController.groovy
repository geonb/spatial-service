/*
 * Copyright (C) 2016 Atlas of Living Australia
 * All Rights Reserved.
 *
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 */

package au.org.ala.spatial.service

import au.com.bytecode.opencsv.CSVWriter
import au.org.ala.RequireLogin
import au.org.ala.web.AuthService
import grails.converters.JSON
import grails.gorm.transactions.Transactional
import org.apache.log4j.Logger

@RequireLogin
class LogController {

    final Logger logger = Logger.getLogger(LogController.class)

    def logService
    AuthService authService
    def serviceAuthService

    /**
     * login required
     *
     * @return
     */
    @Transactional
    def index() {
        def log = new Log(params)
        log.data = request.JSON.toString()

        if (!log.save()) {
            log.errors.each {
                logger.error(it)
            }
        }

        render status: 200
    }

    /**
     * login required
     *
     * @return
     */
    def search() {
        def searchResult = logService.search(params, authService.getUserId(), serviceAuthService.isAdmin(params))

        def totalCount = logService.searchCount(params, authService.getUserId(), serviceAuthService.isAdmin(params))

        if ("application/json".equals(request.getHeader("accept")) || "application/json".equals(params.accept)) {
            def map = [records: searchResult, totalCount: totalCount]
            render map as JSON
        } else if ("application/csv".equals(request.getHeader("accept")) || "application/csv".equals(params.accept)) {
            response.contentType = 'application/csv'
            response.setHeader("Content-disposition", "filename=\"search.csv\"")

            CSVWriter writer = new CSVWriter(new OutputStreamWriter(response.outputStream))
            def firstRow = true
            searchResult.each { row ->
                if (firstRow) {
                    writer.writeNext(row.keySet() as String[])
                    firstRow = false
                }
                writer.writeNext(row.values() as String[])
            }
            writer.flush()
            writer.close()
        } else {
            [searchResult: searchResult, totalCount: totalCount]
        }
    }
}
