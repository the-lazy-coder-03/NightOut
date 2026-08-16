(() => {
    const saveLinks = Array.from(document.querySelectorAll("[data-photo-save]"));

    function supportsFileShare() {
        return typeof navigator.share === "function"
            && typeof navigator.canShare === "function"
            && typeof File === "function";
    }

    function isAppleTouchDevice() {
        return /iPad|iPhone|iPod/.test(navigator.userAgent)
            || (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1);
    }

    function fallbackToBrowser(downloadUrl, shareUrl) {
        window.location.href = isAppleTouchDevice() && shareUrl ? shareUrl : downloadUrl;
    }

    async function savePhoto(event) {
        const link = event.currentTarget;
        const downloadUrl = link.dataset.saveUrl || link.href;
        const shareUrl = link.dataset.shareUrl || downloadUrl;
        const filename = link.dataset.saveFilename || link.getAttribute("download") || "nightout-photo.jpg";
        if (!downloadUrl) return;

        if (!supportsFileShare()) {
            if (isAppleTouchDevice() && shareUrl) {
                event.preventDefault();
                fallbackToBrowser(downloadUrl, shareUrl);
            }
            return;
        }

        event.preventDefault();
        link.setAttribute("aria-busy", "true");
        let shouldFallback = true;

        try {
            const response = await fetch(shareUrl, {credentials: "same-origin"});
            if (!response.ok) throw new Error("Photo download failed.");

            const blob = await response.blob();
            const contentType = blob.type || response.headers.get("Content-Type") || "image/jpeg";
            const file = new File([blob], filename, {type: contentType});
            if (navigator.canShare({files: [file]})) {
                await navigator.share({files: [file], title: filename});
                shouldFallback = false;
                return;
            }
        } catch (error) {
            if (error?.name === "AbortError") {
                shouldFallback = false;
            }
        } finally {
            link.removeAttribute("aria-busy");
        }

        if (shouldFallback) {
            fallbackToBrowser(downloadUrl, shareUrl);
        }
    }

    saveLinks.forEach((link) => {
        link.addEventListener("click", savePhoto);
    });
})();
