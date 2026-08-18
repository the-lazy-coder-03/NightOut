const uploadForms = Array.from(document.querySelectorAll("[data-upload-form]"));
const fileInputs = Array.from(document.querySelectorAll("[data-photo-input]"));
const uploadDialog = document.querySelector("[data-upload-dialog]");
const openUploadDialogButton = document.querySelector("[data-upload-dialog-open]");
const closeUploadDialogButton = document.querySelector("[data-upload-dialog-close]");
const selectedFileInputByForm = new WeakMap();

const parsePositiveInteger = (value, fallback) => {
    const parsed = Number.parseInt(value, 10);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
};

const formatPhotoCount = (count) => `${count} photo${count === 1 ? "" : "s"}`;

const formatMegabytes = (bytes) => Math.max(1, Math.floor(bytes / 1024 / 1024));

const splitFilesIntoBatches = (files, maxFiles, maxBatchBytes) => {
    const batches = [];
    let batch = [];
    let batchBytes = 0;

    files.forEach((file) => {
        const fileBytes = file.size || 0;
        const countLimitReached = maxFiles > 0 && batch.length >= maxFiles;
        const byteLimitReached = maxBatchBytes > 0 && batch.length > 0 && batchBytes + fileBytes > maxBatchBytes;

        if (countLimitReached || byteLimitReached) {
            batches.push(batch);
            batch = [];
            batchBytes = 0;
        }

        batch.push(file);
        batchBytes += fileBytes;
    });

    if (batch.length > 0) {
        batches.push(batch);
    }

    return batches;
};

const selectedFileInputFor = (uploadForm) => {
    const selectedInput = selectedFileInputByForm.get(uploadForm);
    if (selectedInput?.files?.length > 0) {
        return selectedInput;
    }
    return Array.from(uploadForm.querySelectorAll("[data-photo-input]"))
        .find((fileInput) => fileInput.files.length > 0);
};

const buildBatchFormData = (uploadForm, fileInput, batch) => {
    const formData = new FormData(uploadForm);
    Array.from(uploadForm.querySelectorAll("[data-photo-input]")).forEach((input) => {
        if (input.name) {
            formData.delete(input.name);
        }
    });
    batch.forEach((file) => formData.append(fileInput.name || "photos", file, file.name));
    return formData;
};

fileInputs.forEach((fileInput) => {
    fileInput.addEventListener("change", () => {
        const count = fileInput.files.length;
        const form = fileInput.closest("[data-upload-form]");
        const fileSummary = form?.querySelector("[data-photo-summary]");

        if (form) {
            selectedFileInputByForm.set(form, fileInput);
        }

        if (fileSummary) {
            fileSummary.textContent = count === 0 ? "No photos selected" : `${formatPhotoCount(count)} selected`;
        }

        if (count > 0 && fileInput.hasAttribute("data-auto-submit")) {
            form?.requestSubmit();
        }
    });
});

uploadForms.forEach((uploadForm) => {
    uploadForm.addEventListener("submit", async (event) => {
        if (uploadForm.dataset.uploading === "true") {
            event.preventDefault();
            return;
        }

        const submitButton = uploadForm.querySelector("[data-upload-submit]");
        const fileSummary = uploadForm.querySelector("[data-photo-summary]");
        const fileInput = selectedFileInputFor(uploadForm);
        const files = Array.from(fileInput?.files || []);
        const maxFiles = parsePositiveInteger(uploadForm.dataset.uploadMaxFiles, 12);
        const maxFileBytes = parsePositiveInteger(uploadForm.dataset.uploadMaxFileBytes, 0);
        const maxBatchBytes = parsePositiveInteger(uploadForm.dataset.uploadMaxBatchBytes, 0);
        const tooLargeFile = maxFileBytes > 0 ? files.find((file) => file.size > maxFileBytes) : null;

        if (tooLargeFile) {
            event.preventDefault();
            if (fileSummary) {
                fileSummary.textContent = `Each image must be smaller than ${formatMegabytes(maxFileBytes)} MB.`;
            }
            return;
        }

        const batches = splitFilesIntoBatches(files, maxFiles, maxBatchBytes);
        if (batches.length <= 1) {
            if (submitButton) {
                submitButton.disabled = true;
                submitButton.textContent = "Uploading...";
            }

            if (fileSummary) {
                fileSummary.textContent = "Uploading...";
            }
            return;
        }

        event.preventDefault();
        uploadForm.dataset.uploading = "true";

        if (submitButton) {
            submitButton.disabled = true;
            submitButton.textContent = "Uploading...";
        }

        if (fileSummary) {
            fileSummary.textContent = `Uploading batch 1 of ${batches.length}...`;
        }

        let uploadedCount = 0;
        let redirectUrl = uploadForm.dataset.uploadSuccessUrl;

        try {
            for (const [index, batch] of batches.entries()) {
                if (fileSummary) {
                    fileSummary.textContent = `Uploading batch ${index + 1} of ${batches.length}...`;
                }

                const response = await fetch(uploadForm.action, {
                    method: uploadForm.method || "POST",
                    headers: {
                        Accept: "application/json",
                        "X-CrowdCam-Batch-Upload": "true",
                    },
                    body: buildBatchFormData(uploadForm, fileInput, batch),
                });
                const body = await response.json().catch(() => ({}));

                if (!response.ok || body.success === false) {
                    throw new Error(body.message || "Upload failed. Please try again.");
                }

                uploadedCount += Number.isFinite(body.count) ? body.count : batch.length;
                redirectUrl = body.redirectUrl || redirectUrl;
            }

            if (fileSummary) {
                fileSummary.textContent = `${formatPhotoCount(uploadedCount)} uploaded.`;
            }

            window.location.assign(redirectUrl || uploadForm.action);
        } catch (error) {
            uploadForm.dataset.uploading = "false";
            if (submitButton) {
                submitButton.disabled = false;
                submitButton.textContent = "Upload Photos";
            }
            if (fileSummary) {
                fileSummary.textContent = error.message || "Upload failed. Please try again.";
            }
        }
    });
});

openUploadDialogButton?.addEventListener("click", () => {
    if (!uploadDialog) {
        return;
    }
    if (typeof uploadDialog.showModal === "function") {
        uploadDialog.showModal();
    } else {
        uploadDialog.setAttribute("open", "");
    }
});

closeUploadDialogButton?.addEventListener("click", () => {
    if (!uploadDialog) {
        return;
    }
    if (typeof uploadDialog.close === "function") {
        uploadDialog.close();
    } else {
        uploadDialog.removeAttribute("open");
    }
});

uploadDialog?.addEventListener("click", (event) => {
    if (event.target === uploadDialog) {
        if (typeof uploadDialog.close === "function") {
            uploadDialog.close();
        } else {
            uploadDialog.removeAttribute("open");
        }
    }
});
