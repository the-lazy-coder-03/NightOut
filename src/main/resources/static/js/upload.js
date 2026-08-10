const uploadForms = Array.from(document.querySelectorAll("[data-upload-form]"));
const fileInputs = Array.from(document.querySelectorAll("[data-photo-input]"));
const uploadDialog = document.querySelector("[data-upload-dialog]");
const openUploadDialogButton = document.querySelector("[data-upload-dialog-open]");
const closeUploadDialogButton = document.querySelector("[data-upload-dialog-close]");
const uploadChoiceForm = document.querySelector("[data-upload-choice-form]");
const uploadEventSelect = document.querySelector("[data-upload-event-select]");

fileInputs.forEach((fileInput) => {
    fileInput.addEventListener("change", () => {
        const count = fileInput.files.length;
        const form = fileInput.closest("[data-upload-form]");
        const fileSummary = form?.querySelector("[data-photo-summary]");

        if (fileSummary) {
            fileSummary.textContent = count === 0 ? "No photos selected" : `${count} photo${count === 1 ? "" : "s"} selected`;
        }

        if (count > 0 && fileInput.hasAttribute("data-auto-submit")) {
            form?.requestSubmit();
        }
    });
});

uploadForms.forEach((uploadForm) => {
    uploadForm.addEventListener("submit", () => {
        const submitButton = uploadForm.querySelector("[data-upload-submit]");
        const fileSummary = uploadForm.querySelector("[data-photo-summary]");

        if (submitButton) {
            submitButton.disabled = true;
            submitButton.textContent = "Uploading...";
        }

        if (fileSummary) {
            fileSummary.textContent = "Uploading...";
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

uploadEventSelect?.addEventListener("change", () => {
    if (uploadChoiceForm && uploadEventSelect.value) {
        uploadChoiceForm.action = uploadEventSelect.value;
    }
});
