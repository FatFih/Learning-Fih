if (!Android.isLoggedIn()) {
	location.replace("../login-page/login.html");
} else {
	document.getElementById("welcomeMessage").textContent =
		`Üdv, ${Android.getUsername()}!`;
}

document.getElementById("logoutBtn").addEventListener("click", () => {
	Android.logout();
	location.replace("../login-page/login.html");
});
