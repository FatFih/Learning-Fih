if (inApp) {
	document.getElementById("welcomeMessage").textContent = `Üdv, ${Android.getUsername()}!`;
}

document.getElementById("testBtn").addEventListener("click", () => {
	if (inApp) Android.testNotification();
});
