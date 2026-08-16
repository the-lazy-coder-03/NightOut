const buttons = Array.from(document.querySelectorAll("[data-gallery-src]"));
const lightbox = document.querySelector("[data-lightbox]");
const lightboxImage = document.querySelector("[data-lightbox-image]");
const lightboxDownload = document.querySelector("[data-lightbox-download]");
let activeIndex = 0;

function showPhoto(index) {
    if (!lightbox || !lightboxImage || buttons.length === 0) return;
    activeIndex = (index + buttons.length) % buttons.length;
    const activeButton = buttons[activeIndex];
    lightboxImage.src = activeButton.dataset.gallerySrc;
    if (lightboxDownload && activeButton.dataset.galleryDownload) {
        lightboxDownload.href = activeButton.dataset.galleryDownload;
        lightboxDownload.hidden = false;
    } else if (lightboxDownload) {
        lightboxDownload.hidden = true;
    }
    lightbox.classList.add("open");
}

buttons.forEach((button, index) => {
    button.addEventListener("click", () => showPhoto(index));
});

document.querySelectorAll("[data-lightbox-close]").forEach((button) => {
    button.addEventListener("click", () => lightbox?.classList.remove("open"));
});

document.querySelectorAll("[data-lightbox-prev]").forEach((button) => {
    button.addEventListener("click", () => showPhoto(activeIndex - 1));
});

document.querySelectorAll("[data-lightbox-next]").forEach((button) => {
    button.addEventListener("click", () => showPhoto(activeIndex + 1));
});

document.addEventListener("keydown", (event) => {
    if (!lightbox?.classList.contains("open")) return;
    if (event.key === "Escape") lightbox.classList.remove("open");
    if (event.key === "ArrowLeft") showPhoto(activeIndex - 1);
    if (event.key === "ArrowRight") showPhoto(activeIndex + 1);
});
