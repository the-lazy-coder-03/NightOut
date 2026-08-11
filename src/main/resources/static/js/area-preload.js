(() => {
    const sources = Array.from(document.querySelectorAll("[data-preload-src]"))
            .map((element) => element.dataset.preloadSrc)
            .filter(Boolean);

    if (sources.length === 0) {
        return;
    }

    const preloadImages = window.nightOutAreaPreloadImages || [];
    window.nightOutAreaPreloadImages = preloadImages;
    const startPreload = () => {
        new Set(sources).forEach((src) => {
            const image = new Image();
            image.decoding = "async";
            image.src = src;
            preloadImages.push(image);
        });
    };

    if ("requestIdleCallback" in window) {
        window.requestIdleCallback(startPreload, {timeout: 1500});
    } else {
        window.setTimeout(startPreload, 250);
    }
})();
