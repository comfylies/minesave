# Account Center Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move private account controls into a GitHub-inspired standalone account center and create 180px, 90px, and 45px centered avatar images.

**Architecture:** Keep all account mutations under the existing `/api/account` endpoints. Extend the avatar service so a single opaque base key identifies three JPEG objects and expose derived URLs in `UserResponse`. Add a protected `/account` route that owns private profile, avatar, and password UI, leaving `UserProfilePage` public-only.

**Tech Stack:** Spring Boot 3.5, Java 17, JUnit 5/Mockito, Vue 3 Composition API, Vue Router, Pinia, Element Plus, Vite.

---

### Task 1: Specify multi-size avatar behaviour in backend tests

**Files:**
- Modify: `Backend/GameSaves/src/test/java/com/gamesaves/gamesaves/service/impl/AvatarServiceImplTest.java`
- Modify: `Backend/GameSaves/src/test/java/com/gamesaves/gamesaves/dto/response/UserResponseTest.java` (create if absent)

- [ ] **Step 1: Write failing avatar storage and dimensions test**

```java
@Test
void uploadAvatar_storesCentered180pxAvatarAndTwoThumbnails() throws Exception {
    User user = User.builder().id(9L).username("avatar-user").build();
    when(userRepository.findById(9L)).thenReturn(Optional.of(user));
    when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
    when(storageService.getPublicUrl(anyString())).thenAnswer(i -> "/storage/" + i.getArgument(0));

    avatarService.uploadAvatar(9L, new MockMultipartFile("file", "avatar.png", "image/png", landscapePngBytes()));

    ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
    verify(storageService, times(3)).store(keys.capture(), bytes.capture());
    assertThat(keys.getAllValues()).containsExactly(
        "avatars/9/<uuid>-180.jpg", "avatars/9/<uuid>-90.jpg", "avatars/9/<uuid>-45.jpg");
    assertImageSize(bytes.getAllValues().get(0), 180);
    assertImageSize(bytes.getAllValues().get(1), 90);
    assertImageSize(bytes.getAllValues().get(2), 45);
}
```

- [ ] **Step 2: Run the avatar test to verify it fails**

Run: `mvn -Dtest=AvatarServiceImplTest test`

Expected: FAIL because upload currently writes one `*.jpg` object rather than the three required sizes.

- [ ] **Step 3: Write the failing response mapping test**

```java
@Test
void fromEntity_derivesAvatarThumbnailUrls() {
    User user = User.builder()
            .id(9L).avatarKey("avatars/9/a-180.jpg")
            .avatarUrl("/storage/avatars/9/a-180.jpg").build();

    UserResponse response = UserResponse.fromEntity(user);

    assertThat(response.getAvatarUrl()).isEqualTo("/storage/avatars/9/a-180.jpg");
    assertThat(response.getAvatarThumbnailUrl()).isEqualTo("/storage/avatars/9/a-90.jpg");
    assertThat(response.getAvatarSmallUrl()).isEqualTo("/storage/avatars/9/a-45.jpg");
}
```

- [ ] **Step 4: Run the response test to verify it fails**

Run: `mvn -Dtest=UserResponseTest test`

Expected: FAIL because the thumbnail fields do not exist.

- [ ] **Step 5: Commit the red tests**

```bash
git add Backend/GameSaves/src/test/java/com/gamesaves/gamesaves/service/impl/AvatarServiceImplTest.java Backend/GameSaves/src/test/java/com/gamesaves/gamesaves/dto/response/UserResponseTest.java
git commit -m "test: specify avatar image variants"
```

### Task 2: Implement atomic three-size avatar generation

**Files:**
- Modify: `Backend/GameSaves/src/main/java/com/gamesaves/gamesaves/service/impl/AvatarServiceImpl.java`
- Modify: `Backend/GameSaves/src/main/java/com/gamesaves/gamesaves/dto/response/UserResponse.java`

- [ ] **Step 1: Implement a shared center-crop renderer**

```java
private byte[] toSquareJpeg(BufferedImage source, int targetSide) throws IOException {
    int side = Math.min(source.getWidth(), source.getHeight());
    int x = (source.getWidth() - side) / 2;
    int y = (source.getHeight() - side) / 2;
    BufferedImage target = new BufferedImage(targetSide, targetSide, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = target.createGraphics();
    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    graphics.drawImage(source, 0, 0, targetSide, targetSide, x, y, x + side, y + side, null);
    graphics.dispose();
    try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
        ImageIO.write(target, "jpeg", output);
        return output.toByteArray();
    }
}
```

- [ ] **Step 2: Store a generated UUID stem as `avatars/{userId}/{uuid}-180.jpg`, `{uuid}-90.jpg`, and `{uuid}-45.jpg`**

Persist the 180px key in `User.avatarKey` and derive the other keys from its `-180.jpg` suffix. Write all three before changing the entity. On any runtime error, delete only keys written in this invocation, then rethrow.

- [ ] **Step 3: Delete all managed variants during replacement and reset**

```java
private void deleteManagedVariants(String primaryKey) {
    if (primaryKey == null || !primaryKey.matches("avatars/\\d+/[a-f0-9-]+-180\\.jpg")) return;
    storageService.delete(primaryKey);
    storageService.delete(primaryKey.replace("-180.jpg", "-90.jpg"));
    storageService.delete(primaryKey.replace("-180.jpg", "-45.jpg"));
}
```

- [ ] **Step 4: Add thumbnail fields to `UserResponse`**

Add `avatarThumbnailUrl` and `avatarSmallUrl`; derive them only for managed `-180.jpg` URLs. Existing legacy avatar URLs must retain null thumbnail fields instead of being string-rewritten.

- [ ] **Step 5: Run focused tests to verify they pass**

Run: `mvn -Dtest=AvatarServiceImplTest,UserResponseTest test`

Expected: PASS with all added assertions green.

- [ ] **Step 6: Commit the backend implementation**

```bash
git add Backend/GameSaves/src/main/java/com/gamesaves/gamesaves/service/impl/AvatarServiceImpl.java Backend/GameSaves/src/main/java/com/gamesaves/gamesaves/dto/response/UserResponse.java Backend/GameSaves/src/test/java/com/gamesaves/gamesaves/service/impl/AvatarServiceImplTest.java Backend/GameSaves/src/test/java/com/gamesaves/gamesaves/dto/response/UserResponseTest.java
git commit -m "feat: generate avatar thumbnails"
```

### Task 3: Add the protected account-center route and navigation entry

**Files:**
- Modify: `Frontend/GameSaves_Fronted/src/router/index.js`
- Modify: `Frontend/GameSaves_Fronted/src/components/common/AppNavbar.vue`
- Create: `Frontend/GameSaves_Fronted/src/views/AccountCenterPage.vue`

- [ ] **Step 1: Add a route test seam by defining the route contract in the router**

Add this child route beneath `DefaultLayout`:

```js
{ path: 'account', name: 'AccountCenter', component: () => import('../views/AccountCenterPage.vue'), meta: { requiresAuth: true } }
```

Verify manually before UI implementation: unauthenticated `/account` redirects to `/login?redirect=/account` through the existing router guard.

- [ ] **Step 2: Build the account center shell**

Use `ref('profile')` for selected section and render only one panel at a time:

```vue
<div class="account-page container">
  <header class="account-heading"><h1>账户中心</h1><p>管理你的公开资料、头像和登录安全。</p></header>
  <div class="account-layout">
    <nav class="account-nav" aria-label="账户设置">
      <button :class="{ active: section === 'profile' }" @click="section = 'profile'">个人资料</button>
      <button :class="{ active: section === 'avatar' }" @click="section = 'avatar'">头像</button>
      <button :class="{ active: section === 'security' }" @click="section = 'security'">登录与安全</button>
    </nav>
    <section class="account-panel">...</section>
  </div>
</div>
```

- [ ] **Step 3: Move private controls from `UserProfilePage.vue`**

Move the profile form state/actions, hidden avatar input/actions, and password dialog/actions into `AccountCenterPage.vue`. Do not duplicate API calls. Keep self profile display and article list in `UserProfilePage.vue`; remove `账户中心` card, edit button, private dialogs and related imports/state/CSS.

- [ ] **Step 4: Add the dropdown entry and compact avatar source**

Place “账户中心” before “个人主页” in `AppNavbar.vue` and push `/account`. Bind the 32px navigation avatar to `auth.currentUser?.avatarSmallUrl || auth.currentUser?.avatarUrl`.

- [ ] **Step 5: Implement GitHub-style CSS and responsiveness**

Use a centered 900px maximum content width, 180px navigation column, 14px controls, 24px page heading, 16px panel headings, thin border tokens, white panels and horizontal tab overflow below 720px. Do not import or reuse any `views/admin` CSS.

- [ ] **Step 6: Build the frontend to verify compilation**

Run: `npm run build`

Expected: exit code 0.

- [ ] **Step 7: Commit the account-center page**

```bash
git add Frontend/GameSaves_Fronted/src/router/index.js Frontend/GameSaves_Fronted/src/components/common/AppNavbar.vue Frontend/GameSaves_Fronted/src/views/AccountCenterPage.vue Frontend/GameSaves_Fronted/src/views/UserProfilePage.vue
git commit -m "feat: move account controls to dedicated page"
```

### Task 4: Remove the temporary design mock and complete verification

**Files:**
- Delete: `Frontend/GameSaves_Fronted/public/account-center-layout.html`

- [ ] **Step 1: Remove the visual brainstorming mock**

Delete only `Frontend/GameSaves_Fronted/public/account-center-layout.html`; it is not part of the application feature.

- [ ] **Step 2: Run Java 17 focused tests and production build**

Run:

```powershell
$env:JAVA_HOME = 'C:\Users\root\.jdks\ms-17.0.18'
& "$env:JAVA_HOME\bin\java.exe" -version
mvn -Dtest=AvatarServiceImplTest,UserResponseTest test
```

Run: `npm run build`

Expected: Java version 17, backend focused tests pass, frontend build exits 0.

- [ ] **Step 3: Verify in the local browser**

1. Sign in with the seeded local test account.
2. Open the dropdown, confirm “账户中心” opens `/account`.
3. Confirm every left navigation item swaps the right panel without a route change.
4. Confirm the public profile has no private account controls.
5. Confirm the avatar selector and password dialog render; do not submit an actual password change without explicit action-time confirmation.

- [ ] **Step 4: Commit mock removal and any verification-only cleanup**

```bash
git add -u Frontend/GameSaves_Fronted/public/account-center-layout.html
git commit -m "chore: remove account center design mock"
```
