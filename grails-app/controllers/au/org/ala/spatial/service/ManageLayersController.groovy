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

import au.org.ala.RequireAdmin
import au.org.ala.RequireLogin
import au.org.ala.layers.dao.FieldDAO
import au.org.ala.layers.dao.LayerDAO
import grails.converters.JSON
import grails.converters.XML
import grails.core.GrailsApplication
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.hibernate.criterion.CriteriaSpecification
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser

import java.text.SimpleDateFormat

@RequireAdmin
class ManageLayersController {

    ManageLayersService manageLayersService
    FieldDAO fieldDao
    LayerDAO layerDao
    FileService fileService
    TasksService tasksService
    GrailsApplication grailsApplication
    ServiceAuthService serviceAuthService

    /**
     * admin only or api_key
     *
     * @return
     */
    def index() {
    }

    /**
     * admin only
     *
     * @return
     */
    def layers() {
        Map map = [:]
        map.put("layers", manageLayersService.getAllLayers(null))

        map
    }

    /**
     * admin only
     *
     * @return
     */
    @RequireAdmin
    def remote() {
        if (!params?.remoteUrl) params.remoteUrl = grailsApplication.config.spatialService.remote

        def remote = manageLayersService.getAllLayers(params?.remoteUrl)
        def local = manageLayersService.getAllLayers(null)

        def all = [:]
        remote.each { a ->
            a.fields.each { b ->
                def m = b.toMap()
                (m["layer"] = a)
                all.put(b.id, [remote: m, local: null])
            }
        }
        local.each { a ->
            a.fields.each { b ->
                def m = b.toMap()
                (m["layer"] = a)
                def item = all.get(b.id) ?: [remote: null, local: m]
                item.local = m
                all.put(b.id, item)
            }
        }

        def layersLocalOnly = []
        def layersRemoteOnly = []
        def layersBoth = []
        all.each { k, v ->
            def item = [id      : k, local: v.local, remote: v.remote,
                        layerId : v?.local?.spid ?: v?.remote?.spid,
                        dt_added: v?.local?.last_update ?: v?.remote?.last_update,
                        name    : v?.local?.name ?: v?.remote?.name]
            if (v.local && v.remote) {
                layersBoth.push(item)
            } else if (v.local) {
                layersLocalOnly.push(item)
            } else {
                layersRemoteOnly.push(item)
            }
        }

        Map map = [:]
        map.put("layersLocalOnly", layersLocalOnly)
        map.put("layersRemoteOnly", layersRemoteOnly)
        map.put("layersBoth", layersBoth)
        map.put("spatialServiceUrl", params.remoteUrl)
        map.put("localUrl", grailsApplication.config.grails.serverURL)

        map
    }

    /**
     * admin only
     *
     * @return
     */

    @RequireAdmin
    def uploads() {
        Map map = [:]

        map.put("files", manageLayersService.listUploadedFiles())

        map
    }

    /**
     * query status, or start download, of local copy of id,names_and_lsid,longitude,latitude
     * that is used in various background processes
     *
     * admin only
     *
     * @return
     */
    @RequireAdmin
    def records() {
        Map map = [:]

        //create a task to get status or download records
        if (params.containsKey('download')) {
            //create task
            Task task = tasksService.create('DownloadRecords', '', [])

            render task as JSON
        }

        File f = new File((grailsApplication.config.data.dir + '/sampling/records.csv') as String)
        if (f.exists()) {
            map.put("last_refreshed", new SimpleDateFormat("dd/MM/yyyy hh:mm:ss").format(new Date(f.lastModified())))
        } else {
            map.put("last_refreshed", "never")
        }

        //TODO: include status of the existing, waiting or running, 'DownloadRecords' task

        render map as JSON
    }

    /**
     * Upload of a shapefile or bil file in a zip
     * <p/>
     * Creates temporary geoserver entry for display.
     * <p/>
     * Returns map of:
     * id, for future reference.
     * columns, listing of column name containted in the .dbf
     * url, for display and inspection
     *
     * admin only
     *
     * @param req
     * @param apiKey
     * @return
     * @throws Exception
     */
    @RequireAdmin
    def upload() {
        log.info("Receiving upload of zip file")
        String id = String.valueOf(System.currentTimeMillis())

        def file
        try {
            file = request.getFile('file')
            log.info("Receiving upload of zip file: " + file)
            File uploadPath = new File((grailsApplication.config.data.dir + '/uploads/' + id) as String)
            File uploadFile = new File((grailsApplication.config.data.dir + '/uploads/' + id + '/id.zip') as String)
            uploadPath.mkdirs()
            file.transferTo(uploadFile)

            log.info("Unzipping upload of zip file...")
            fileService.unzip(uploadFile.getPath(), uploadFile.getParent(), true)
            log.info("Unzipped upload of zip file.")

            //delete uploaded zip now that it has been unzipped
            uploadFile.delete()
            log.info("Deleting original zip. File moved to: " + grailsApplication.config.data.dir + '/uploads/' + id )

            def result = manageLayersService.processUpload(uploadFile.getParentFile(), id)
            if (result.error){
                log.error("Problem processing upload. " + result.error)
                return redirect(action: 'uploads', params: [error: result.error])
            }
        } catch (err) {
            log.error 'upload failed - ' + err.getMessage(), err
            return redirect(action: 'uploads', params: [error: err.getMessage() + " - see logs for details"])
        }

        redirect(action: 'layer', id: id, params: [message: 'Upload successful'])
    }

    /**
     * importLayer with values from layers-service/layer/{id} url
     * if /data/spatial-data/uploads/{id}/{id}.* does not exist an attempt will be made to copy it from
     * /data/ala/data/layers/ready/shape or /diva
     *
     * admin only
     *
     * @return
     */
    @RequireAdmin
    def importLayer() {
        JSONParser jp = new JSONParser()
        InputStream is = new URL(params.url.toString()).openStream()
        String str = ""
        try {
            str = IOUtils.toString(is)
        } finally {
            is.close()
        }

        JSONObject jo = (JSONObject) jp.parse(str)
        String id = jo.get('name')
        jo.put('raw_id', id)
        jo.put('requestedId', String.valueOf(jo.get('id')))

        def input = [:]

        for (def key : jo.keySet()) {
            input.put(key, jo.get(key))
        }

        String dir = grailsApplication.config.data.dir + '/uploads/' + id
        File uploadDir = new File(dir)

        if (!uploadDir.exists()) {
            new File(dir).mkdirs()

            for (File src : new File('/data/ala/data/layers/ready/shape/').listFiles()) {
                if (src.getName().startsWith(id + '.')) {
                    FileUtils.copyFile(src, new File((uploadDir + '/' + src.getName()) as String))
                }
            }

            for (File src : new File('/data/ala/data/layers/ready/diva/').listFiles()) {
                if (src.getName().startsWith(id + '.')) {
                    FileUtils.copyFile(src, new File((uploadDir + '/' + src.getName()) as String))
                }
            }
        }

        Map map = [:]
        map.putAll manageLayersService.createOrUpdateLayer(input, id)

        render map as JSON
    }

    /**
     * importField with values from layers-service/field/{id} url
     * layer must exist
     *
     * admin only
     *
     * @return
     */
    @RequireAdmin
    def importField() {
        JSONParser jp = new JSONParser()
        InputStream is = new URL(params.url.toString()).openStream()
        String str = ""
        try {
            str = IOUtils.toString(is)
        } finally {
            is.close()
        }

        JSONObject jo = (JSONObject) jp.parse(str)
        jo.put('requestedId', jo.get('id'))
        if (jo.containsKey('sname')) jo.put('sid', jo.get('sname'))

        String id = jo.get("spid")
        jo.put('raw_id', id)

        Map input = [:]

        for (String key : jo.keySet()) {
            input.put(key, jo.get(key))
        }

        Map map = [:]
        map.putAll manageLayersService.createOrUpdateField(input, id)

        render map as JSON
    }

    /**
     * create/update (POST) or get (GET) layer
     *
     * admin only
     *
     * @param id
     * @return
     */
    @RequireAdmin
    def layer(String id) {
        String layerId = id
        Map map = [:]
        if ("POST".equalsIgnoreCase(request.method)) {
            if (params.containsKey("name")) {
                map.putAll manageLayersService.createOrUpdateLayer(params, layerId)
            } else {
                map.putAll manageLayersService.createOrUpdateLayer(request.JSON as Map, layerId)
            }
            redirect(action: 'layers', params: map)
        }

        //show
        map.putAll manageLayersService.layerMap(layerId)

        //tasks
        if (map.containsKey('layer_id')) {
            List list = Task.createCriteria().list() {
                resultTransformer(CriteriaSpecification.ALIAS_TO_ENTITY_MAP)

                createAlias('input', 'input')

                projections {
                    groupProperty('id', 'id')
                    groupProperty('message', 'message')
                    groupProperty('status', 'status')
                    groupProperty('name', 'name')
                    groupProperty('created', 'created')
                    groupProperty('tag', 'tag')
                }

                eq('input.value', String.valueOf(map.get('layer_id')))
            }
            map.put('task', list)
        } else {
            map.put('task', [])
        }

        withFormat {
            html { map }
            js { render map as JSON }
            json { render map as JSON }
            xml { render map as XML }
        }
    }

    /**
     * delete field with fieldId, layer with layerId, distribution with data_resource_uid
     * @param id
     * @return
     */
    def deleteUpload(String id) {
        delete(id, "uploads")
    }

    /**
     * delete field with fieldId, layer with layerId, distribution with data_resource_uid
     * @param id
     * @return
     */
    def deleteLayer(String id) {
        delete(id, "layers")
    }

    /**
     * delete field with fieldId, layer with layerId, distribution with data_resource_uid
     *
     * admin only
     *
     * @param id
     * @return
     */
    @RequireAdmin
    def delete(String id, String action) {
        if (fieldDao.getFieldById(id, false) == null) {
            Map m = manageLayersService.getUpload(id, false)
            if (m == null || (!m.containsKey('data_resource_uid') && !m.containsKey('checklist'))) {
                manageLayersService.deleteLayer(id)
            } else if (!m.containsKey('checklist')) {
                manageLayersService.deleteDistribution(id)
            } else {
                manageLayersService.deleteChecklist(id)
            }
            redirect(action: action)
        } else {
            def layerId = fieldDao.getFieldById(id, false).spid
            manageLayersService.deleteField(id)

            redirect(action: action, id: layerId)
        }
    }


    /**
     * create/update (POST) or get (GET) field
     *
     * admin only
     *
     * @param id
     * @return
     */
    @RequireAdmin
    def field(String id) {
        Map map = [:]
        Map layer = id.startsWith('cl') || id.startsWith('el') ? [:] : manageLayersService.layerMap(id)
        if (layer.size() < 2) {
            //this is a field id
            if ("POST".equalsIgnoreCase(request.method)) {
                //update field
                if (params.containsKey("name")) {
                    map.putAll manageLayersService.createOrUpdateField(params, id)
                } else {
                    map.putAll manageLayersService.createOrUpdateField(request.JSON as Map, id)
                }
                redirect(action: 'layers')
            }

            map.putAll(manageLayersService.fieldMap(id))

        } else {
            //this is a layer id
            if ("POST".equalsIgnoreCase(request.method)) {
                //add field to layerId
                if (params.containsKey("name")) {
                    map.putAll manageLayersService.createOrUpdateField(params, id)
                } else {
                    map.putAll manageLayersService.createOrUpdateField(request.JSON as Map, id)
                }

                //redirect to this newly created field
//                redirect(action: 'field', id: map.id)
                redirect(action: 'layers')

            } else {
                map.putAll(manageLayersService.fieldMapDefault(id))
            }
        }

        //tasks
        if (map.containsKey('layer_id')) {
            def list = Task.createCriteria().list() {
                resultTransformer(CriteriaSpecification.ALIAS_TO_ENTITY_MAP)

                createAlias('input', 'input')

                projections {
                    groupProperty('id', 'id')
                    groupProperty('message', 'message')
                    groupProperty('status', 'status')
                    groupProperty('name', 'name')
                    groupProperty('created', 'created')
                    groupProperty('tag', 'tag')
                }

                eq('input.value', String.valueOf(id))
            }
            map.put('task', list)
        } else {
            map.put('task', [])
        }

        withFormat {
            html { map }
            js { render map as JSON }
            json { render map as JSON }
            xml { render map as XML }
        }
    }

    /**
     * create/update (POST) or get (GET) distribution
     *
     * admin only
     *
     * @param id
     * @return
     */
    @RequireAdmin
    def distribution(String id) {
        String uploadId = id
        Map map = [:]
        if ("POST".equalsIgnoreCase(request.method)) {
            if (params.containsKey("data_resource_uid")) {
                map.putAll manageLayersService.createDistribution(params, uploadId)
            } else {
                map.putAll manageLayersService.createDistribution(request.JSON as Map, uploadId)
            }

            //redirect to this newly created distribution
            redirect(action: 'distribution', id: map.data_resource_uid)
        }

        //show
        map.putAll manageLayersService.distributionMap(uploadId)

        //tasks
        if (map.containsKey('data_resource_uid')) {
            def list = Task.createCriteria().list() {
                resultTransformer(CriteriaSpecification.ALIAS_TO_ENTITY_MAP)

                createAlias('input', 'input')

                projections {
                    groupProperty('id', 'id')
                    groupProperty('message', 'message')
                    groupProperty('status', 'status')
                    groupProperty('name', 'name')
                    groupProperty('created', 'created')
                    groupProperty('tag', 'tag')
                }

                eq('input.value', String.valueOf(map.get('data_resource_uid')))
            }
            map.put('task', list)
        } else {
            map.put('task', [])
        }

        withFormat {
            html { map }
            js { render map as JSON }
            json { render map as JSON }
            xml { render map as XML }
        }
    }

    /**
     * create/update (POST) or get (GET) distribution
     *
     * admin only
     *
     * @param id
     * @return
     */
    @RequireAdmin
    def checklist(String id) {
        String uploadId = id
        Map map = [:]
        if ("POST".equalsIgnoreCase(request.method)) {
            if (params.containsKey("data_resource_uid")) {
                map.putAll manageLayersService.createChecklist(params, uploadId)
            } else {
                map.putAll manageLayersService.createChecklist(request.JSON as Map, uploadId)
            }

            //redirect to this newly created distribution
            redirect(action: 'checklist', id: map.data_resource_uid)
        }

        //show
        map.putAll manageLayersService.checklistMap(uploadId)

        //tasks
        if (map.containsKey('checklist')) {
            def list = Task.createCriteria().list() {
                resultTransformer(CriteriaSpecification.ALIAS_TO_ENTITY_MAP)

                createAlias('input', 'input')

                projections {
                    groupProperty('id', 'id')
                    groupProperty('message', 'message')
                    groupProperty('status', 'status')
                    groupProperty('name', 'name')
                    groupProperty('created', 'created')
                    groupProperty('tag', 'tag')
                }

                eq('input.value', String.valueOf(map.get('checklist')))
            }
            map.put('task', list)
        } else {
            map.put('task', [])
        }

        withFormat {
            html { map }
            js { render map as JSON }
            json { render map as JSON }
            xml { render map as XML }
        }
    }

    /**
     * Copy a remote field to local file system, local geoserver and local postgres.
     *
     * If it already exists it will be updated.
     *
     * admin only
     */
    @RequireAdmin
    def copy() {
        def spatialServiceUrl = params.spatialServiceUrl;
        def fieldId = params.fieldId;

        manageLayersService.updateFromRemote(spatialServiceUrl, fieldId)
        redirect(controller: "Tasks", action: "index")

    }

    /**
     * admin only
     *
     * @return
     */
    @RequireAdmin
    def enable() {
        if (params.id.isNumber()) {
            def layer = layerDao.getLayerById(params.id.toInteger(), false)
            layer.enabled = true
            layerDao.updateLayer(layer)
        } else {
            def field = fieldDao.getFieldById(params.id, false)
            field.enabled = true
            fieldDao.updateField(field)
        }

        render ''
    }

}
