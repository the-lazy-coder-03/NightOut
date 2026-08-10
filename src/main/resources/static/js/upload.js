const fileInput = document.querySelector("[data-photo-input]");
const fileSummary = document.querySelector("[data-photo-summary]");
const uploadForm = document.querySelector("[data-upload-form]");
const submitButton = document.querySelector("[data-upload-submit]");

fileInput?.addEventListener("change", () => {
    const count = fileInput.files.length;
    if (fileSummary) {
        fileSummary.textContent = count === 0 ? "No photos selected" : `${count} photo${count === 1 ? "" : "s"} selected`;
    }
});

uploadForm?.addEventListener("submit", () => {
    if (submitButton) {
        submitButton.disabled = true;
        submitButton.textContent = "Uploading...";
    }
});
