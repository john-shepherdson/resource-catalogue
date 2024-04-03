package gr.uoa.di.madgik.resourcecatalogue.controllers.registry;

import gr.uoa.di.madgik.resourcecatalogue.annotations.Browse;
import gr.uoa.di.madgik.resourcecatalogue.domain.VocabularyCuration;
import gr.uoa.di.madgik.resourcecatalogue.utils.FacetFilterUtils;
import gr.uoa.di.madgik.resourcecatalogue.service.VocabularyCurationService;
import gr.uoa.di.madgik.registry.domain.FacetFilter;
import gr.uoa.di.madgik.registry.domain.Paging;
import io.swagger.annotations.Api;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import springfox.documentation.annotations.ApiIgnore;

import java.util.Set;

@RestController
@RequestMapping("vocabularyCuration")
@Api(value = "Get information about a Vocabulary Curation")
public class VocabularyCurationController extends ResourceController<VocabularyCuration, Authentication> {

    private static final Logger logger = LogManager.getLogger(VocabularyCurationController.class);
    private VocabularyCurationService<VocabularyCuration, Authentication> vocabularyCurationService;

    @Autowired
    VocabularyCurationController(VocabularyCurationService<VocabularyCuration, Authentication> service) {
        super(service);
        this.vocabularyCurationService = service;
    }

    //    @ApiOperation(value = "Get Vocabulary Curation by ID")
    @GetMapping(path = "{id}", produces = {MediaType.APPLICATION_JSON_VALUE})
    @Override
    public ResponseEntity<VocabularyCuration> get(@PathVariable("id") String id, @ApiIgnore Authentication authentication) {
        return new ResponseEntity<>(vocabularyCurationService.get(id), HttpStatus.OK);
    }

    @Override
//    @ApiOperation(value = "Creates a new Vocabulary Curation Request.")
    @PostMapping(produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
    public ResponseEntity<VocabularyCuration> add(@RequestBody VocabularyCuration vocabularyCuration, @ApiIgnore Authentication auth) {
        ResponseEntity<VocabularyCuration> ret = super.add(vocabularyCuration, auth);
        logger.info("Adding new Vocabulary Curation");
        return ret;
    }

    //    @ApiOperation(value = "Creates a new Vocabulary Curation Request (front-end use).")
    @PostMapping(path = "addFront", produces = {MediaType.APPLICATION_JSON_VALUE})
    public VocabularyCuration addFront(@RequestParam(required = false) String resourceId, @RequestParam(required = false) String providerId,
                                       @RequestParam String resourceType, @RequestParam String entryValueName, @RequestParam String vocabulary,
                                       @RequestParam(required = false) String parent, @ApiIgnore Authentication auth) {
        logger.info("Adding new Vocabulary Curation (UI)");
        return vocabularyCurationService.addFront(resourceId, providerId, resourceType, entryValueName, vocabulary, parent, auth);
    }

    //    @ApiOperation(value = "Filter a list of Vocabulary Curation Requests based on a set of filters or get a list of all Vocabulary Curation Requests in the Catalogue.")
    @Browse
    @GetMapping(path = "vocabularyCurationRequests/all", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
    @PreAuthorize("hasRole('ROLE_ADMIN') or hasRole('ROLE_EPOT')")
    public ResponseEntity<Paging<VocabularyCuration>> getAllVocabularyCurationRequests(@ApiIgnore @RequestParam MultiValueMap<String, Object> allRequestParams, @RequestParam(required = false) Set<String> status,
                                                                                       @RequestParam(required = false) Set<String> vocabulary, @ApiIgnore Authentication authentication) {
        FacetFilter ff = FacetFilterUtils.createFacetFilter(allRequestParams);
        return ResponseEntity.ok(vocabularyCurationService.getAllVocabularyCurationRequests(ff, authentication));
    }

    @PutMapping(path = "approveOrRejectVocabularyCuration", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
    @PreAuthorize("hasRole('ROLE_ADMIN') or hasRole('ROLE_EPOT')")
    public void approveOrRejectVocabularyCuration(@RequestBody VocabularyCuration vocabularyCuration, @RequestParam boolean approved,
                                                  @RequestParam(required = false) String rejectionReason, @ApiIgnore Authentication authentication) {
        vocabularyCurationService.approveOrRejectVocabularyCuration(vocabularyCuration, approved, rejectionReason, authentication);
    }

}