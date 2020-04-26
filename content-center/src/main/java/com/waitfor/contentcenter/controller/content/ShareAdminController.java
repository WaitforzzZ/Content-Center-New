package com.waitfor.contentcenter.controller.content;

import com.waitfor.contentcenter.auth.CheckAuthorization;
import com.waitfor.contentcenter.domain.dto.content.ShareAuditDTO;
import com.waitfor.contentcenter.domain.entity.content.Share;
import com.waitfor.contentcenter.service.content.ShareService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/shares")
public class ShareAdminController {
    @Autowired
    private ShareService shareService;
    @PutMapping("/audit/{id}")
    @CheckAuthorization("admin")
    public Share auditById(@PathVariable Integer id, @RequestBody ShareAuditDTO auditDTO){
        return this.shareService.auditById(id,auditDTO);
    }
}
