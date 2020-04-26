package com.waitfor.contentcenter.controller.content;

import com.waitfor.contentcenter.auth.CheckLogin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.waitfor.contentcenter.domain.dto.content.ShareDTO;
import com.waitfor.contentcenter.service.content.ShareService;

@RestController
@RequestMapping("/shares")
public class ShareController {

	@Autowired
	private ShareService shareService;
	@GetMapping("/{id}")
	@CheckLogin
	public ShareDTO findById(@PathVariable Integer id){
		
		return this.shareService.findById(id);
	}
}
