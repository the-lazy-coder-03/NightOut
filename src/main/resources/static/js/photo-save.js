(() => {
    const saveLinks = Array.from(document.querySelectorAll("[data-photo-save]"));

    function supportsFileShare() {
        return typeof navigator.share === "function"
            && typeof navigator.canShare === "function"
            && typeof File === "function";
    }

    async function savePhoto(event) {
        const link = event.currentTarget;
        const url = link.dataset.saveUrl || link.href;
        const filename = link.dataset.saveFilename || link.getAttribute("download") || "nightout-photo.jpg";
        if (!url || !supportsFileShare()) return;

        event.preventDefault();
        link.setAttribute("aria-busy", "true");

        try {
            const response = await fetch(url, {credentials: "same-origin"});
            if (!response.ok) throw new Error("Photo download failed.");

            const blob = await response.blob();
            const file = new File([blob], filename, {type: blob.type || "image/jpeg"});
            if (navigator.canShare({files: [file]})) {
                await navigator.share({files: [file], title: filename});
                return;
            }
        } catch (error) {
            if (error?.name === "AbortError") return;
        } finally {
            link.removeAttribute("aria-busy");
        }

        window.location.href = url;
    }

    saveLinks.forEach((link) => {
        link.addEventListener("click", savePhoto);
    });
})();
