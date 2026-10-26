package org.kaleta.rest;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.kaleta.rest.dto.CaseEntryUpdateDto;
import org.kaleta.rest.dto.CaseTableUpdateDto;
import org.kaleta.rest.error.InvalidInputException;
import org.kaleta.rest.validation.ValidId;
import org.kaleta.service.CaseEntryService;
import org.kaleta.service.ImageService;
import org.kaleta.service.PeriodContentService;

import java.io.IOException;
import java.nio.file.Files;

@Path("/case/entry")
@Produces(MediaType.APPLICATION_JSON)
public class CaseEntryEndpoints
{
    @Inject
    CaseEntryService caseEntryService;

    @PUT
    @Path("/{caseId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response save(
            @NotNull @ValidId @PathParam("caseId") Long caseId,
            @Valid @NotNull CaseEntryUpdateDto dto)
    {
        return Response.ok(caseEntryService.save(caseId, dto)).build();
    }

    @PUT
    @Path("/{entryId}/date")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response moveEntry(
            @NotNull @ValidId @PathParam("entryId") Long entryId,
            @Valid @NotNull CaseEntryUpdateDto dto)
    {
        return Response.ok(caseEntryService.moveEntry(entryId, dto.getDate())).build();
    }

    @POST
    @Path("/{entryId}/image")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response addImage(
            @NotNull @ValidId @PathParam("entryId") Long entryId,
            @RestForm("file") FileUpload file)
    {
        if (file == null) {
            throw new InvalidInputException("no file was uploaded");
        }
        if (file.size() > ImageService.MAX_UPLOAD_BYTES) {
            throw new InvalidInputException("the image is larger than "
                    + (ImageService.MAX_UPLOAD_BYTES / (1024 * 1024)) + "MB");
        }

        byte[] uploaded;
        try {
            uploaded = Files.readAllBytes(file.uploadedFile());
        } catch (IOException exception) {
            throw new InvalidInputException("the upload could not be read: " + exception.getMessage());
        }
        return Response.status(Response.Status.CREATED)
                .entity(caseEntryService.addImage(entryId, uploaded, file.contentType()))
                .build();
    }

    @POST
    @Path("/{entryId}/table")
    public Response addTable(@NotNull @ValidId @PathParam("entryId") Long entryId)
    {
        return Response.status(Response.Status.CREATED)
                .entity(caseEntryService.addTable(entryId))
                .build();
    }

    @PUT
    @Path("/table/{tableId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateTable(
            @NotNull @ValidId @PathParam("tableId") Long tableId,
            @Valid @NotNull CaseTableUpdateDto dto)
    {
        return Response.ok(caseEntryService.updateTable(tableId, dto)).build();
    }

    @DELETE
    @Path("/table/{tableId}")
    public Response deleteTable(@NotNull @ValidId @PathParam("tableId") Long tableId)
    {
        caseEntryService.deleteTable(tableId);
        return Response.ok().build();
    }

    @DELETE
    @Path("/image/{imageId}")
    public Response deleteImage(@NotNull @ValidId @PathParam("imageId") Long imageId)
    {
        caseEntryService.deleteImage(imageId);
        return Response.ok().build();
    }

    /** The picture itself; its bytes never change once stored, so it caches for good. */
    @GET
    @Path("/image/{imageId}")
    @Produces({"image/png", "image/jpeg", MediaType.APPLICATION_JSON})
    public Response image(
            @NotNull @ValidId @PathParam("imageId") Long imageId,
            @QueryParam("thumbnail") boolean thumbnail)
    {
        PeriodContentService.Picture picture = caseEntryService.readImage(imageId, thumbnail);
        return Response.ok(picture.bytes())
                .type(picture.mime())
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
                .build();
    }
}
