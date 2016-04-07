

grails.project.groupId = "au.org.ala" // change this to alter the default package name and Maven publishing destination
appName = "spatial-service"

grails.appName = appName

default_config = "/data/${appName}/config/${appName}-config.properties"
if (!grails.config.locations || !(grails.config.locations instanceof List)) {
    grails.config.locations = []
}
if (new File(default_config).exists()) {
    println "[${appName}] Including default configuration file: " + default_config;
    grails.config.locations.add "file:" + default_config
} else {
    println "[${appName}] No external configuration file defined."
}

grails.project.groupId = appName // change this to alter the default package name and Maven publishing destination

// The ACCEPT header will not be used for content negotiation for user agents containing the following strings (defaults to the 4 major rendering engines)
grails.mime.disable.accept.header.userAgents = ['Gecko', 'WebKit', 'Presto', 'Trident']
grails.mime.use.accept.header = true
grails.mime.types = [ // the first one is the default format
                      all          : '*/*', // 'all' maps to '*' or the first available format in withFormat
                      atom         : 'application/atom+xml',
                      css          : 'text/css',
                      csv          : 'text/csv',
                      form         : 'application/x-www-form-urlencoded',
                      html         : ['text/html', 'application/xhtml+xml'],
                      js           : 'text/javascript',
                      json         : ['application/json', 'text/json'],
                      multipartForm: 'multipart/form-data',
                      rss          : 'application/rss+xml',
                      text         : 'text/plain',
                      hal          : ['application/hal+json', 'application/hal+xml'],
                      xml          : ['text/xml', 'application/xml']
]

// URL Mapping Cache Max Size, defaults to 5000
//grails.urlmapping.cache.maxsize = 1000

// What URL patterns should be processed by the resources plugin
grails.resources.adhoc.patterns = ['/images/*', '/css/*', '/js/*', '/plugins/*']
grails.resources.adhoc.includes = ['/images/**', '/css/**', '/js/**', '/plugins/**']

// Legacy setting for codec used to encode data with ${}
grails.views.default.codec = "html"

// The default scope for controllers. May be prototype, session or singleton.
// If unspecified, controllers are prototype scoped.
grails.controllers.defaultScope = 'singleton'

//grails.config.locations = [ "file:/data/spatial-service/config/spatial-service-config.properties"]

// GSP settings
grails {
    views {
        gsp {
            encoding = 'UTF-8'
            htmlcodec = 'xml' // use xml escaping instead of HTML4 escaping
            codecs {
                expression = 'html' // escapes values inside ${}
                scriptlet = 'html' // escapes output from scriptlets in GSPs
                taglib = 'none' // escapes output from taglibs
                staticparts = 'none' // escapes output from static template parts
            }
        }
        // escapes all not-encoded output at final stage of outputting
        // filteringCodecForContentType.'text/html' = 'html'
    }
}


grails.converters.encoding = "UTF-8"
// scaffolding templates configuration
grails.scaffolding.templates.domainSuffix = 'Instance'

// Set to false to use the new Grails 1.2 JSONBuilder in the render method
grails.json.legacy.builder = false
// enabled native2ascii conversion of i18n properties files
grails.enable.native2ascii = true
// packages to include in Spring bean scanning
grails.spring.bean.packages = []
// whether to disable processing of multi part requests
grails.web.disable.multipart = false

// request parameters to mask when logging exceptions
grails.exceptionresolver.params.exclude = ['password']

// configure auto-caching of queries by default (if false you can cache individual queries with 'cache: true')
grails.hibernate.cache.queries = false

// configure passing transaction's read-only attribute to Hibernate session, queries and criterias
// set "singleSession = false" OSIV mode in hibernate configuration after enabling
grails.hibernate.pass.readonly = false
// configure passing read-only to OSIV session by default, requires "singleSession = false" OSIV mode
grails.hibernate.osiv.readonly = false

environments {
    development {
        grails.logging.jul.usebridge = true
        grails.serverURL = 'http://localhost:8080/spatial-service'
    }
    production {
        grails.logging.jul.usebridge = false
        grails.serverURL = 'http://spatial-test.ala.org.au/spatial-service'
        // TODO: grails.serverURL = "http://www.changeme.com"
    }
}

// log4j configuration
log4j = {
    appenders {
        console name: 'stdout', layout: pattern(conversionPattern: '%d %c{1} %m%n')
    }

    error 'org.codehaus.groovy.grails.web.servlet',        // controllers
            'org.codehaus.groovy.grails.web.pages',          // GSP
            'org.codehaus.groovy.grails.web.sitemesh',       // layouts
            'org.codehaus.groovy.grails.web.mapping.filter', // URL mapping
            'org.codehaus.groovy.grails.web.mapping',        // URL mapping
            'org.codehaus.groovy.grails.commons',            // core / classloading
            'org.codehaus.groovy.grails.plugins',            // plugins
            'org.codehaus.groovy.grails.orm.hibernate',      // hibernate integration
            'org.springframework',
            'org.hibernate',
            'net.sf.ehcache.hibernate'
    error 'org', 'net'

    all 'au.org.ala.layers', 'au.org.ala.spatial' //, 'grails.app'
}

//
// au.org.ala.spatial.service config
//
data.dir = '/data/spatial-data'
//geoserver.url = 'http://local.ala.org.au:8080/geoserver'
geoserver.url = 'http://local.ala.org.au:8079/geoserver'
geoserver.username = ''
geoserver.password = ''
geoserver.canDeploy = true
//gdal.dir="/usr/bin/"
shpResolutions = [0.5, 0.25, 0.1, 0.05]
grdResolutions = [0.5, 0.25, 0.1, 0.05, 0.01]
//biocacheServiceUrl = 'http://biocache.ala.org.au/ws'
//biocacheServiceUrl = 'http://ala-cohen.it.csiro.au/biocache-service'
biocacheServiceUrl = 'http://biocache.ala.org.au/ws'
//biocacheServiceUrl = 'http://ala-starr.it.csiro.au:8080/biocache-service'
biocacheUrl = 'http://biocache.ala.org.au'

slave.enable = true
service.enable = true

serviceKey = ""
batch_sampling_passwords = ''
batch_sampling_points_limit = 1000000
batch_sampling_fields_limit = 1000

//
// au.org.ala.spatial.slave config
//
//spatialService.url = "http://spatial-test.ala.org.au/spatial-service"
spatialService.url = "http://localhost:8080/spatial-service"
data.dir = "/data/spatial-data"
shp2pgsql.path = "/usr/bin/shp2pgsql"
gdal.dir = "/usr/bin/"
aloc.xmx = "6G"
aloc.threads = 4
maxent.mx = "1G"
maxent.threads = 4

slaveKey = ""
serviceKey = ""

// time between pushing status updates to the master for a task
statusTime = 3000
retryCount = 10
retryTime = 30000

/******************************************************************************\
 *  CAS SETTINGS
 *
 *  NOTE: Some of these will be ignored if default_config exists
 \******************************************************************************/
security.cas.casServerName = 'https://auth.ala.org.au'
security.cas.authenticateOnlyIfLoggedInFilterPattern = '/manageLayers, /manageLayers/.*'
//security.cas.uriExclusionFilterPattern = '/images.*,/css.*,/js.*'
security.cas.loginUrl = 'https://auth.ala.org.au/cas/login'
security.cas.contextPath = '/spatial-service'
security.cas.serverName = 'http://local.ala.org.au:8080'
security.cas.logoutUrl = 'https://auth.ala.org.au/cas/logout'
security.cas.casServerUrlPrefix = 'https://auth.ala.org.au/cas'
security.cas.bypass = false // set to true for non-ALA deployment
security.cas.appServerName = 'http://local.ala.org.au:8080'
security.cas.casServerUrlPrefix = 'https://auth.ala.org.au/cas'
security.cas.casProperties = 'casServerLoginUrl,serverName,centralServer,casServerName,uriFilterPattern,uriExclusionFilter,authenticateOnlyIfLoggedInFilterPattern,casServerLoginUrlPrefix,gateway,casServerUrlPrefix,contextPath'
auth.admin_role = "ROLE_ADMIN"


headerAndFooter.baseURL = 'http://www2.ala.org.au/commonui-bs3'
ala.baseURL = 'http://www.ala.org.au'
bie.baseURL = 'http://bie.ala.org.au'
bie.searchPath = '/search'


records.url = 'http://biocache.ala.org.au/archives/exports/lat_lon_taxon.zip'

api_key = ''
layersService.url = 'http://local.ala.org.au:8080/spatial-service'
lists.url = 'http://lists.ala.org.au'
collections.url = 'http://collections.ala.org.au'
sandboxUrl = 'http://sandbox.ala.org.au/ala-hub'
sandboxServiceUrl = 'http://sandbox.ala.org.au/biocache-service'

gazField = 'cl915'
userObjectsField = 'cl1082'

apiKeyCheckUrlTemplate = 'http://auth.ala.org.au/apikey/ws/check?apikey={0}'

wkhtmltopdf.path = "/usr/local/bin/wkhtmltopdf"