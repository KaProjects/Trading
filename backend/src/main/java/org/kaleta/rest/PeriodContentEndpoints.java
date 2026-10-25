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
import org.kaleta.rest.dto.PeriodContentUpdateDto;
import org.kaleta.rest.error.InvalidInputException;
import org.kaleta.rest.validation.ValidId;
import org.kaleta.service.ImageService;
import org.kaleta.service.PeriodContentService;

import java.io.IOException;
import java.nio.file.Files;

@Path("/period/content")
@Produces(MediaType.APPLICATION_JSON)
public class PeriodContentEndpoints
{
    @Inject
    PeriodContentService periodContentService;

    @PUT
    @Path("/{periodId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response save(
            @NotNull @ValidId @PathParam("periodId") Long periodId,
            @Valid @NotNull PeriodContentUpdateDto dto)
    {
        return Response.ok(periodContentService.save(periodId, dto)).build();
    }

    @POST
    @Path("/{contentId}/image")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response addImage(
            @NotNull @ValidId @PathParam("contentId") Long contentId,
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
                .entity(periodContentService.addImage(contentId, uploaded, file.contentType()))
                .build();
    }

    @DELETE
    @Path("/image/{imageId}")
    public Response deleteImage(@NotNull @ValidId @PathParam("imageId") Long imageId)
    {
        periodContentService.deleteImage(imageId);
        return Response.ok().build();
    }

    /**
     * The picture itself. Its bytes never change once stored - a new picture is a new id - so it
     * can be cached for as long as the browser likes.
     */
    @GET
    @Path("/image/{imageId}")
    @Produces({"image/png", "image/jpeg", MediaType.APPLICATION_JSON})
    public Response image(
            @NotNull @ValidId @PathParam("imageId") Long imageId,
            @QueryParam("thumbnail") boolean thumbnail)
    {
        PeriodContentService.Picture picture = periodContentService.readImage(imageId, thumbnail);
        return Response.ok(picture.bytes())
                .type(picture.mime())
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
                .build();
    }
}
